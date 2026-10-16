package soulflameaudio;

import com.google.inject.Provides;
import java.awt.Color;
import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.RuneLite;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Soulflame Horn",
	description = "Sounds, overhead text and a radius overlay for the Soulflame horn's Entice special attack",
	tags = {"soulflame", "horn", "yama", "entice", "sound", "audio", "special", "overlay"}
)
public class SoulflameHornAudioPlugin extends Plugin
{
	static final String SENT_SOUND = "entice-sent";
	static final String APPLIED_SOUND = "entice-applied";
	static final String CONSUMED_SOUND = "entice-consumed";
	static final String WHIFF_SOUND = "entice-whiff";
	static final String FADED_SOUND = "entice-faded";
	static final String ALLY_WASTED_SOUND = "entice-ally-wasted";
	static final File SOUND_DIR = new File(RuneLite.RUNELITE_DIR, SoulflameHornAudioConfig.GROUP);

	/** Where decoded copies of mp3 files are kept, out of the sound folder so they are never picked. */
	private static final File DECODED_DIR = new File(RuneLite.CACHE_DIR, SoulflameHornAudioConfig.GROUP);

	private static final String BUNDLED_SOUND_PATH = "/soulflameaudio/";
	private static final String WAV = ".wav";

	/** What java.sound decodes for the audio player, and mp3, which is decoded to .wav first. */
	private static final String[] AUDIO_EXTENSIONS = {".wav", ".mp3", ".au", ".aif", ".aiff"};

	/**
	 * Entice empowers the next melee attack made within 10 ticks. The server announces the expiry
	 * itself, so this only exists to drop stale state if that message is ever missed.
	 */
	private static final int ENTICE_DURATION_TICKS = 10;

	/** A game tick is 600ms, used to turn the tick counter into seconds for display. */
	private static final int MILLIS_PER_TICK = 600;

	// Compared against the lowercased game message. The ally variant is prefixed with their name.
	private static final String SELF_CAST = "you encourage nearby allies";
	private static final String ALLY_CAST = "encourages you with their soulflame horn";
	private static final String NO_LISTENERS = "you blow your horn into the wind";
	private static final String EFFECT_FADED = "soulflame horn's effect fades away";

	/** Splits "<name> encourages you with their soulflame horn." to recover the caster. */
	private static final String ALLY_CAST_NAME_SUFFIX = " encourages you with";
	/**
	 * The game can colour the caster's name with a code such as {@code @mes_hl_blu@}, which
	 * {@link Text#removeTags} leaves in place. Player names cannot contain {@code @}.
	 */
	private static final Pattern COLOUR_CODE = Pattern.compile("@[a-z0-9_]+@");

	/** Where the radius colour was saved before its opacity covered the outline too. */
	private static final String LEGACY_RADIUS_COLOUR = "radiusColour";

	/** Roughly two seconds of overhead text, matching how long the game shows its own. */
	private static final int OVERHEAD_CYCLES = 120;

	/** Overhead text comes from another player's config, so it does not get to be arbitrarily long. */
	private static final int MAX_OVERHEAD_CHARS = 80;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SoulflameHornAudioConfig config;

	@Inject
	private AudioPlayer audioPlayer;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private PartyService partyService;

	@Inject
	private WSClient wsClient;

	@Inject
	private ItemManager itemManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SoulflameHornRadiusOverlay radiusOverlay;

	@Inject
	private SoulflameHornReminderOverlay reminderOverlay;

	private SoulflameHornInfoBox infoBox;

	private int enticeTicksRemaining;

	/** Who gave us the current buff, or null when we blew the horn ourselves. */
	private String enticeCaster;

	/** The tick the current buff was given, so only attacks made since then say how it was used. */
	private int enticeCastTick;

	/** A hit of ours that may have spent the buff, decided at the end of its tick. See {@link #onGameTick}. */
	private boolean hitPending;
	private int hitTick;

	/**
	 * A wasted horn can fade for several people at once, us and every party member it reached. Only the
	 * first of those plays a sound, so one blow never makes more than one noise when it goes to waste.
	 */
	private boolean expirySoundPlayed;

	/** The horn radius each party member has shared, by member id, for those wearing a configured horn. */
	private final Map<Long, Integer> partyRadii = new HashMap<>();

	/** The radius we last shared with the party, zero meaning none, which is also what a new party assumes. */
	private int sharedRadius;

	/** Last seen experience per skill, to tell a gain from the values the client reports on login. */
	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);

	/** The latest tick the player gained melee experience, and ranged or magic experience. */
	private int lastMeleeXpTick = -1;
	private int lastRangedOrMagicXpTick = -1;

	@Provides
	SoulflameHornAudioConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SoulflameHornAudioConfig.class);
	}

	@Override
	protected void startUp()
	{
		enticeTicksRemaining = 0;
		enticeCaster = null;
		hitPending = false;
		lastXp.clear();
		migrateRadiusColour();
		partyRadii.clear();
		sharedRadius = 0;
		wsClient.registerMessage(EnticeStatusMessage.class);
		wsClient.registerMessage(HornRadiusMessage.class);
		overlayManager.add(radiusOverlay);
		overlayManager.add(reminderOverlay);

		infoBox = new SoulflameHornInfoBox(itemManager.getImage(ItemID.SOULFLAME_HORN), this, this);
		infoBoxManager.addInfoBox(infoBox);

		if (!SOUND_DIR.exists() && !SOUND_DIR.mkdirs())
		{
			log.warn("Could not create sound override directory {}", SOUND_DIR);
		}

		// Decoded ahead of time, so even the first play of an mp3 in the sound folder is instant.
		executor.execute(() -> decodeAll(SOUND_DIR));
	}

	private static void decodeAll(File directory)
	{
		final File[] mp3s = directory.listFiles(Mp3ToWav::isMp3);
		if (mp3s == null)
		{
			return;
		}

		for (File mp3 : mp3s)
		{
			try
			{
				Mp3ToWav.convert(mp3, DECODED_DIR);
			}
			catch (Exception e)
			{
				log.warn("Could not decode {}", mp3, e);
			}
		}
	}

	/**
	 * The radius colour's opacity once shaded only the area, with the outline always solid. It now
	 * fades both, so a colour saved the old way moves to the new setting at full opacity, and its old
	 * opacity becomes the fill's share, which draws it just as before.
	 */
	void migrateRadiusColour()
	{
		final Color old = configManager.getConfiguration(SoulflameHornAudioConfig.GROUP, LEGACY_RADIUS_COLOUR, Color.class);
		if (old == null)
		{
			return;
		}

		configManager.setConfiguration(SoulflameHornAudioConfig.GROUP, SoulflameHornAudioConfig.RADIUS_COLOUR,
			new Color(old.getRed(), old.getGreen(), old.getBlue()));
		configManager.setConfiguration(SoulflameHornAudioConfig.GROUP, SoulflameHornAudioConfig.RADIUS_FILL_OPACITY,
			Math.round(old.getAlpha() * 100f / 255));
		configManager.unsetConfiguration(SoulflameHornAudioConfig.GROUP, LEGACY_RADIUS_COLOUR);
	}

	@Override
	protected void shutDown()
	{
		enticeTicksRemaining = 0;
		enticeCaster = null;
		wsClient.unregisterMessage(EnticeStatusMessage.class);
		wsClient.unregisterMessage(HornRadiusMessage.class);
		partyRadii.clear();
		overlayManager.remove(radiusOverlay);
		overlayManager.remove(reminderOverlay);
		infoBoxManager.removeInfoBox(infoBox);
		infoBox = null;
	}

	boolean isEnticeActive()
	{
		return enticeTicksRemaining > 0;
	}

	boolean isInfoBoxEnabled()
	{
		return config.showInfobox();
	}

	/** Rounded up, so a buff with any time left never reads as zero seconds. */
	int getEnticeSecondsRemaining()
	{
		return (enticeTicksRemaining * MILLIS_PER_TICK + 999) / 1000;
	}

	/** The bare number for the infobox, in whichever unit is configured. */
	String getEnticeCountdown()
	{
		return Integer.toString(config.countdownUnit() == CountdownUnit.TICKS
			? enticeTicksRemaining
			: getEnticeSecondsRemaining());
	}

	/** The radius in tiles, or zero while the horn has never been configured. */
	int getHornRadius()
	{
		return client.getVarbitValue(VarbitID.YAMA_HORN_RADIUS);
	}

	/**
	 * The horn reports no radius until it has been configured, and the configure option only appears
	 * while it is equipped. Distinguishing that from a real radius is what lets the reminder explain
	 * an otherwise blank overlay.
	 */
	boolean isHornRadiusConfigured()
	{
		return getHornRadius() > 0;
	}

	/**
	 * Read from the equipment container rather than a varbit, so this reflects what is actually worn
	 * rather than relying on the meaning of a varbit name.
	 */
	boolean isHornEquipped()
	{
		final ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return false;
		}

		final Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		return weapon != null && weapon.getId() == ItemID.SOULFLAME_HORN;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		final String message = event.getMessage().toLowerCase();

		if (message.contains(SELF_CAST) || message.contains(ALLY_CAST))
		{
			final boolean ourOwnHorn = !message.contains(ALLY_CAST);

			enticeTicksRemaining = ENTICE_DURATION_TICKS;
			enticeCaster = ourOwnHorn ? null : casterFrom(event.getMessage());
			enticeCastTick = client.getTickCount();
			hitPending = false;
			expirySoundPlayed = false;
			log.debug("Entice applied by {}, expires in {} ticks",
				ourOwnHorn ? "self" : enticeCaster, ENTICE_DURATION_TICKS);

			// Blowing your own horn buffs you as well, but only one of the two events should fire.
			if (ourOwnHorn)
			{
				announce(EnticeStatus.SENT);

				if (config.playSentSound())
				{
					play(SENT_SOUND, config.sentFile());
				}
			}
			else
			{
				announce(EnticeStatus.RECEIVED);

				if (config.playAppliedSound())
				{
					play(APPLIED_SOUND, config.appliedFile());
				}
			}
		}
		else if (message.contains(EFFECT_FADED))
		{
			// The server only sends this when the buff went unused, so it needs no state of its own.
			enticeTicksRemaining = 0;
			log.debug("Entice wasted");

			announce(EnticeStatus.WASTED);
			enticeCaster = null;

			if (config.playFadedSound())
			{
				playExpiry(FADED_SOUND, config.fadedFile());
			}
		}
		else if (message.contains(NO_LISTENERS))
		{
			announce(EnticeStatus.MISSED);

			if (config.playWhiffSound())
			{
				play(WHIFF_SOUND, config.whiffFile());
			}
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (enticeTicksRemaining <= 0)
		{
			return;
		}

		// Damage we dealt to something else. Damage we take carries the same "mine" tint, hence the actor check.
		final Hitsplat hitsplat = event.getHitsplat();
		if (!hitsplat.isMine() || event.getActor() == client.getLocalPlayer())
		{
			return;
		}

		hitPending = true;
		hitTick = client.getTickCount();
	}

	/**
	 * Experience lands when an attack is made, so it shows which style the player is attacking with.
	 * Melee trains Attack, Strength or Defence. Ranged and magic train their own skill, and their
	 * defensive styles add Defence alongside it.
	 */
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		final Integer previous = lastXp.put(event.getSkill(), event.getXp());
		if (previous == null || event.getXp() <= previous)
		{
			return;
		}

		switch (event.getSkill())
		{
			case ATTACK:
			case STRENGTH:
			case DEFENCE:
				lastMeleeXpTick = client.getTickCount();
				break;
			case RANGED:
			case MAGIC:
				lastRangedOrMagicXpTick = client.getTickCount();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Before the countdown, so a hit on the buff's last tick still counts.
		if (hitPending)
		{
			hitPending = false;
			if (enticeTicksRemaining > 0 && hitWasMelee())
			{
				spend();
			}
		}

		if (enticeTicksRemaining > 0)
		{
			enticeTicksRemaining--;
		}

		shareRadius();
	}

	/**
	 * Tells the party our horn's radius whenever it changes, and zero once the horn comes off. With
	 * sharing turned off, a radius already shared is withdrawn and nothing more is sent.
	 */
	private void shareRadius()
	{
		if (!partyService.isInParty())
		{
			return;
		}

		final int radius = config.partyIntegration() && isHornEquipped() ? getHornRadius() : 0;
		if (radius != sharedRadius)
		{
			sharedRadius = radius;
			partyService.send(new HornRadiusMessage(radius));
		}
	}

	@Subscribe
	public void onHornRadiusMessage(HornRadiusMessage event)
	{
		clientThread.invokeLater(() ->
		{
			final PartyMember local = partyService.getLocalMember();
			if (local != null && event.getMemberId() == local.getMemberId())
			{
				return;
			}

			if (event.getRadius() > 0)
			{
				partyRadii.put(event.getMemberId(), event.getRadius());
			}
			else
			{
				partyRadii.remove(event.getMemberId());
			}
		});
	}

	/** Someone new knows nothing of our horn, so it is shared again on the next tick. */
	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		clientThread.invokeLater(() -> sharedRadius = 0);
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		clientThread.invokeLater(() -> partyRadii.remove(event.getMemberId()));
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		clientThread.invokeLater(() ->
		{
			partyRadii.clear();
			sharedRadius = 0;
		});
	}

	/** The party members on screen wearing a configured horn, with the radius each of them set. */
	Map<Player, Integer> partyHornRadii()
	{
		final Map<Player, Integer> shown = new HashMap<>();
		for (Map.Entry<Long, Integer> entry : partyRadii.entrySet())
		{
			final PartyMember member = partyService.getMemberById(entry.getKey());
			final Player player = member == null ? null : findPlayer(member.getDisplayName());
			if (player != null)
			{
				shown.put(player, entry.getValue());
			}
		}
		return shown;
	}

	/**
	 * Entice only empowers melee, so a ranged or magic hit leaves it waiting for the next melee one.
	 * A hit counts when melee experience landed with it, or the tick before, no earlier than the horn,
	 * and is the latest combat experience. A tie means Defence came with Ranged or Magic, from longrange
	 * or defensive casting. A hit with no experience at all is not taken as melee: thralls deal damage
	 * that is ours but gives none. A melee hit of 0, or at 200m experience, gives none either, so it
	 * goes unannounced rather than a thrall spending the buff.
	 */
	private boolean hitWasMelee()
	{
		return lastMeleeXpTick >= enticeCastTick
			&& lastMeleeXpTick >= hitTick - 1
			&& lastMeleeXpTick > lastRangedOrMagicXpTick;
	}

	private void spend()
	{
		enticeTicksRemaining = 0;
		log.debug("Entice spent");

		announce(EnticeStatus.SPENT);
		enticeCaster = null;

		if (config.playConsumedSound())
		{
			play(CONSUMED_SOUND, config.consumedFile());
		}
	}

	/** Shows our own line locally and, when in a party, tells everyone else so they can show it too. */
	private void announce(EnticeStatus status)
	{
		final String overheadText = ourTextFor(status);
		if (config.showOwnOverheadText())
		{
			showOverheadTextOn(client.getLocalPlayer(), overheadText);
		}

		if (!config.partyIntegration() || !partyService.isInParty())
		{
			return;
		}

		// a miss is our own horn, even while we hold someone else's buff
		final String caster = status == EnticeStatus.MISSED ? null : enticeCaster;
		partyService.send(new EnticeStatusMessage(status, caster, overheadText));
	}

	@Subscribe
	public void onEnticeStatusMessage(EnticeStatusMessage event)
	{
		// Party messages arrive on the network thread. Handled on the client thread like everything
		// else, a report never races our own fade to the one wasted sound, and reads the game safely.
		clientThread.invokeLater(() -> handleReport(event));
	}

	private void handleReport(EnticeStatusMessage event)
	{
		if (!config.partyIntegration() || event.getMemberId() == partyService.getLocalMember().getMemberId())
		{
			return;
		}

		final PartyMember sender = partyService.getMemberById(event.getMemberId());
		final String senderName = sender == null ? null : sender.getDisplayName();
		log.debug("{} reports {}", senderName, event.getStatus());

		// The sound is about our own buff going to waste, so it only fires for the player who cast it.
		// The text is shown to the whole party, including others who were buffed by the same horn.
		final boolean ours = weCast(event);
		if (event.getStatus() == EnticeStatus.WASTED && config.playAllyWastedSound() && ours)
		{
			playExpiry(ALLY_WASTED_SOUND, config.allyWastedFile());
		}

		if (config.showAllyOverheadText() && senderName != null)
		{
			showOverheadText(senderName, textFor(event, ours));
		}
	}

	/**
	 * Our own wording by default, so what appears on screen is what this client configured. Their
	 * wording is only used when we opt in, and even then ours covers a line an older version sent blank.
	 */
	private String textFor(EnticeStatusMessage event, boolean weCast)
	{
		if (config.useOthersDialogue())
		{
			final String theirs = sanitizeOverheadText(event.getOverheadText());
			if (theirs != null)
			{
				return theirs;
			}
		}

		// Somebody squandering a buff we handed them is its own moment, so it gets its own line.
		if (weCast && event.getStatus() == EnticeStatus.WASTED)
		{
			return orDefault(config.partyWastedOverheadText(), SoulflameHornAudioConfig.PARTY_WASTED_TEXT);
		}

		return ourTextFor(event.getStatus());
	}

	private String ourTextFor(EnticeStatus status)
	{
		switch (status)
		{
			case SENT:
				return orDefault(config.sentOverheadText(), SoulflameHornAudioConfig.SENT_TEXT);
			case MISSED:
				return orDefault(config.whiffOverheadText(), SoulflameHornAudioConfig.MISSED_TEXT);
			case RECEIVED:
				return orDefault(config.appliedOverheadText(), SoulflameHornAudioConfig.RECEIVED_TEXT);
			case SPENT:
				return orDefault(config.spentOverheadText(), SoulflameHornAudioConfig.UTILIZED_TEXT);
			case WASTED:
				return orDefault(config.wastedOverheadText(), SoulflameHornAudioConfig.WASTED_TEXT);
			default:
				return null;
		}
	}

	// a line left blank shows its default rather than nothing
	private static String orDefault(String line, String fallback)
	{
		return line == null || line.trim().isEmpty() ? fallback : line;
	}

	private boolean weCast(EnticeStatusMessage event)
	{
		final Player localPlayer = client.getLocalPlayer();
		return localPlayer != null
			&& localPlayer.getName() != null
			&& event.getCasterName() != null
			// plain on both sides, as a client on an older version sends the name with its colour code
			&& plainName(localPlayer.getName()).equalsIgnoreCase(plainName(event.getCasterName()));
	}

	private static String plainName(String name)
	{
		return Text.sanitize(COLOUR_CODE.matcher(name).replaceAll("")).trim();
	}

	/**
	 * The sender picks this text, so strip anything that could misrender on our screen and cap the
	 * length rather than trusting another client's config verbatim.
	 */
	private static String sanitizeOverheadText(String text)
	{
		if (text == null)
		{
			return null;
		}

		final String plain = Text.removeTags(text).trim();
		if (plain.isEmpty())
		{
			return null;
		}

		return plain.length() > MAX_OVERHEAD_CHARS ? plain.substring(0, MAX_OVERHEAD_CHARS) : plain;
	}

	/** Client side only, so the text appears above that player for us and nobody else. */
	private void showOverheadText(String playerName, String text)
	{
		showOverheadTextOn(findPlayer(playerName), text);
	}

	private Player findPlayer(String name)
	{
		if (name == null)
		{
			return null;
		}

		final String wanted = plainName(name);
		for (Player player : client.getTopLevelWorldView().players())
		{
			if (player.getName() != null && plainName(player.getName()).equalsIgnoreCase(wanted))
			{
				return player;
			}
		}
		return null;
	}

	private void showOverheadTextOn(Player player, String text)
	{
		if (player == null || text == null || text.trim().isEmpty())
		{
			return;
		}

		player.setOverheadText(text.trim());
		player.setOverheadCycle(OVERHEAD_CYCLES);
	}

	/** Returns the caster from an ally cast message, or null if the wording no longer matches. */
	private static String casterFrom(String rawMessage)
	{
		final String plain = Text.removeTags(rawMessage);
		final int end = plain.toLowerCase().indexOf(ALLY_CAST_NAME_SUFFIX);
		if (end <= 0)
		{
			return null;
		}

		final String name = plainName(plain.substring(0, end));
		return name.isEmpty() ? null : name;
	}

	/** Plays the sound for a buff going to waste, unless another fade from the same blow already has. */
	private void playExpiry(String bundled, String customStem)
	{
		if (expirySoundPlayed)
		{
			return;
		}

		expirySoundPlayed = true;
		play(bundled, customStem);
	}

	private void play(String bundled, String customStem)
	{
		final int volume = config.volume();
		if (!config.enableSounds() || volume <= 0)
		{
			return;
		}

		final float gain = 20f * (float) Math.log10(volume / 100f);
		final File override = config.useCustomSounds() ? pickOverride(stemFor(bundled, customStem)) : null;

		if (override == null)
		{
			playBundled(bundled, gain);
		}
		else if (Mp3ToWav.isMp3(override))
		{
			// Decoding can take a moment the first time, which the game should never wait on.
			executor.execute(() -> playDecoded(override, bundled, gain));
		}
		else
		{
			playFile(override, gain);
		}
	}

	private void playDecoded(File mp3, String bundled, float gain)
	{
		final File wav;
		try
		{
			wav = Mp3ToWav.convert(mp3, DECODED_DIR);
		}
		catch (Exception e)
		{
			log.warn("Could not decode {}, playing the bundled sound instead", mp3, e);
			playBundled(bundled, gain);
			return;
		}

		playFile(wav, gain);
	}

	// AudioPlayer declares checked exceptions from the sound API, which the plugin hub does not allow
	// us to name. Catching Exception covers them without importing them, and a failed sound is only
	// ever logged either way.
	private void playFile(File file, float gain)
	{
		try
		{
			audioPlayer.play(file, gain);
		}
		catch (Exception e)
		{
			log.warn("Failed to play {}", file, e);
		}
	}

	private void playBundled(String bundled, float gain)
	{
		try
		{
			audioPlayer.play(getClass(), BUNDLED_SOUND_PATH + bundled + WAV, gain);
		}
		catch (Exception e)
		{
			log.warn("Failed to play {}", bundled, e);
		}
	}

	private static String stemFor(String bundled, String customStem)
	{
		if (customStem == null)
		{
			return bundled;
		}

		final String unquoted = unquote(customStem);
		return unquoted.isEmpty() ? bundled : unquoted;
	}

	/**
	 * Windows' Copy as path wraps a path in double quotes, and people type single ones too, so any
	 * quotes around the setting are dropped.
	 */
	static String unquote(String setting)
	{
		String value = setting.trim();
		while (!value.isEmpty() && isQuote(value.charAt(0)))
		{
			value = value.substring(1).trim();
		}
		while (!value.isEmpty() && isQuote(value.charAt(value.length() - 1)))
		{
			value = value.substring(0, value.length() - 1).trim();
		}
		return value;
	}

	private static boolean isQuote(char c)
	{
		return c == '"' || c == '\'';
	}

	/**
	 * Resolves the user's clip for an event, or null to fall back to the bundled sound. Accepts a
	 * full path to a sound file, a full path to a folder to pick from, or a bare name looked up in
	 * the plugin's own sound folder.
	 */
	private static File pickOverride(String setting)
	{
		return pickOverride(SOUND_DIR, setting);
	}

	/** Takes the folder explicitly so the resolution can be exercised against a real directory. */
	static File pickOverride(File soundDir, String setting)
	{
		final File asPath = new File(setting);
		if (asPath.isAbsolute())
		{
			if (asPath.isDirectory())
			{
				return randomClipIn(asPath, null);
			}

			if (!asPath.isFile())
			{
				return null;
			}

			if (!isSupportedAudio(asPath.getName().toLowerCase()))
			{
				log.warn("{} is not a format the plugin can play; convert it to .wav or .mp3", asPath);
				return null;
			}

			return asPath;
		}

		return randomClipIn(soundDir, setting);
	}

	private static boolean isSupportedAudio(String lowerName)
	{
		for (String extension : AUDIO_EXTENSIONS)
		{
			if (lowerName.endsWith(extension))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Picks a random sound from a folder. With a stem, {@code name} and any {@code name-*} qualify in
	 * any supported format, so dropping in several variants gives a different one each time.
	 */
	private static File randomClipIn(File directory, String stem)
	{
		final String wanted = stem == null ? null : stem.toLowerCase();

		final File[] candidates = directory.listFiles((dir, name) ->
		{
			final String lower = name.toLowerCase();
			if (!isSupportedAudio(lower))
			{
				return false;
			}

			if (wanted == null || lower.startsWith(wanted + "-"))
			{
				return true;
			}

			return lower.substring(0, lower.lastIndexOf('.')).equals(wanted);
		});

		if (candidates == null || candidates.length == 0)
		{
			return null;
		}

		return candidates[ThreadLocalRandom.current().nextInt(candidates.length)];
	}
}

