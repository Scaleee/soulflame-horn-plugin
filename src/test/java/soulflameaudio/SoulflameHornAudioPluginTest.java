package soulflameaudio;

import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.io.File;
import java.util.Arrays;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.StatChanged;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class SoulflameHornAudioPluginTest
{
	// Also verbatim from a live session.
	private static final String SELF_CAST =
		"<col=3366ff>You encourage nearby allies, which also empowers your next melee attack!";
	// Verbatim from a live session, save for the name. Note the colour tag and capital S.
	private static final String ALLY_CAST =
		"<col=0000b2>Zezima encourages you with their Soulflame horn, empowering your next melee attack!";
	private static final String NO_LISTENERS =
		"You blow your horn into the wind. No-one nearby is able to listen.";
	// Colour tags are present on the real messages, which is why matching is done on lowercase substrings.
	private static final String EFFECT_FADED =
		"<col=3366ff>The Soulflame horn's effect fades away.";

	@Mock
	@Bind
	private Client client;

	@Mock
	@Bind
	private ClientThread clientThread;

	@Mock
	@Bind
	private AudioPlayer audioPlayer;

	@Mock
	@Bind
	private SoulflameHornAudioConfig config;

	@Mock
	@Bind
	private PartyService partyService;

	@Mock
	@Bind
	private WSClient wsClient;

	@Mock
	@Bind
	private ItemManager itemManager;

	@Mock
	@Bind
	private InfoBoxManager infoBoxManager;

	@Mock
	@Bind
	private OverlayManager overlayManager;

	@Mock
	@Bind
	private SoulflameHornRadiusOverlay radiusOverlay;

	@Mock
	@Bind
	private SoulflameHornReminderOverlay reminderOverlay;

	@Mock
	private Player localPlayer;

	@Mock
	private Player allyPlayer;

	@Mock
	private NPC target;

	@Mock
	private Hitsplat hitsplat;

	@Mock
	private ItemContainer equipment;

	@Mock
	private WorldView worldView;

	@Mock
	private IndexedObjectSet<Player> players;

	@Inject
	private SoulflameHornAudioPlugin plugin;

	private int tick;

	@Before
	public void setUp()
	{
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

		when(client.getLocalPlayer()).thenReturn(localPlayer);
		when(config.playAppliedSound()).thenReturn(true);
		when(config.playSentSound()).thenReturn(true);
		when(config.playConsumedSound()).thenReturn(true);
		when(config.playFadedSound()).thenReturn(true);
		when(config.enableSounds()).thenReturn(true);
		when(config.volume()).thenReturn(100);
		when(hitsplat.isMine()).thenReturn(true);
		when(client.getTickCount()).thenAnswer(invocation -> tick);
		// Work handed to the client thread runs straight away, so what it does can be checked directly.
		doAnswer(invocation ->
		{
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
	}

	@Test
	public void theHornCountsAsEquippedWhenItIsInTheWeaponSlot()
	{
		givenWorn(net.runelite.api.gameval.ItemID.SOULFLAME_HORN);

		assertTrue(plugin.isHornEquipped());
	}

	@Test
	public void anotherWeaponDoesNotCountAsTheHorn()
	{
		givenWorn(net.runelite.api.gameval.ItemID.SOULFLAME_HORN + 1);

		assertFalse(plugin.isHornEquipped());
	}

	@Test
	public void anEmptyWeaponSlotDoesNotCountAsTheHorn()
	{
		when(client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN)).thenReturn(equipment);
		when(equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx())).thenReturn(null);

		assertFalse(plugin.isHornEquipped());
	}

	@Test
	public void noEquipmentAtAllDoesNotCountAsTheHorn()
	{
		when(client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN)).thenReturn(null);

		assertFalse(plugin.isHornEquipped());
	}

	@Test
	public void anUnconfiguredHornReportsNoRadius()
	{
		when(client.getVarbitValue(net.runelite.api.gameval.VarbitID.YAMA_HORN_RADIUS)).thenReturn(0);

		assertEquals(0, plugin.getHornRadius());
		assertFalse("this is what makes the reminder show instead of a blank overlay",
			plugin.isHornRadiusConfigured());
	}

	@Test
	public void aConfiguredHornReportsItselfConfigured()
	{
		when(client.getVarbitValue(net.runelite.api.gameval.VarbitID.YAMA_HORN_RADIUS)).thenReturn(5);

		assertTrue(plugin.isHornRadiusConfigured());
	}

	@Test
	public void aConfiguredRadiusIsUsedAsGiven()
	{
		when(client.getVarbitValue(net.runelite.api.gameval.VarbitID.YAMA_HORN_RADIUS)).thenReturn(5);

		assertEquals(5, plugin.getHornRadius());
	}

	private void givenWorn(int itemId)
	{
		when(client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN)).thenReturn(equipment);
		when(equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx())).thenReturn(new Item(itemId, 1));
	}

	@Test
	public void theBuffCountsDownInWholeSeconds()
	{
		plugin.onChatMessage(gameMessage(SELF_CAST));
		assertEquals(6, plugin.getEnticeSecondsRemaining());

		plugin.onGameTick(new GameTick());
		plugin.onGameTick(new GameTick());
		assertEquals("8 ticks left rounds up to 5s, never down to 4", 5, plugin.getEnticeSecondsRemaining());
	}

	@Test
	public void theOverlayKnowsWhenTheBuffIsUp()
	{
		assertFalse(plugin.isEnticeActive());

		plugin.onChatMessage(gameMessage(SELF_CAST));
		assertTrue(plugin.isEnticeActive());

		plugin.onChatMessage(gameMessage(EFFECT_FADED));
		assertFalse(plugin.isEnticeActive());
	}

	@Test
	public void theInfoboxToggleIsSeparateFromBuffState()
	{
		when(config.showInfobox()).thenReturn(false);

		plugin.onChatMessage(gameMessage(SELF_CAST));

		assertFalse(plugin.isInfoBoxEnabled());
		assertTrue("buff state must not depend on the infobox setting", plugin.isEnticeActive());
	}

	@Test
	public void anUnusedBuffExpiringPlaysTheWastedSound()
	{
		plugin.onChatMessage(gameMessage(SELF_CAST));
		plugin.onChatMessage(gameMessage(EFFECT_FADED));

		verifyPlayed(SoulflameHornAudioPlugin.FADED_SOUND);
	}

	@Test
	public void theWastedSoundCanBeTurnedOff()
	{
		when(config.playFadedSound()).thenReturn(false);

		plugin.onChatMessage(gameMessage(EFFECT_FADED));

		verifyNotPlayed(SoulflameHornAudioPlugin.FADED_SOUND);
	}

	@Test
	public void aFadedBuffCannotThenBeSpent()
	{
		plugin.onChatMessage(gameMessage(SELF_CAST));
		plugin.onChatMessage(gameMessage(EFFECT_FADED));
		landMeleeHit(target);

		verifyNotPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void wastingYourOwnBuffShowsYourMessageAboveYourHead()
	{
		when(config.showOwnOverheadText()).thenReturn(true);
		when(config.wastedOverheadText()).thenReturn("Oh, behave!");

		plugin.onChatMessage(gameMessage(SELF_CAST));
		plugin.onChatMessage(gameMessage(EFFECT_FADED));

		verify(localPlayer).setOverheadText("Oh, behave!");
	}

	@Test
	public void yourOwnOverheadTextNeedsNoParty()
	{
		when(config.showOwnOverheadText()).thenReturn(true);
		when(config.partyIntegration()).thenReturn(false);
		when(partyService.isInParty()).thenReturn(false);
		when(config.wastedOverheadText()).thenReturn("Groovy");

		plugin.onChatMessage(gameMessage(ALLY_CAST));
		plugin.onChatMessage(gameMessage(EFFECT_FADED));

		verify(localPlayer).setOverheadText("Groovy");
	}

	@Test
	public void yourOwnOverheadTextCanBeTurnedOff()
	{
		when(config.showOwnOverheadText()).thenReturn(false);
		when(config.wastedOverheadText()).thenReturn("Oh, behave!");

		plugin.onChatMessage(gameMessage(SELF_CAST));
		plugin.onChatMessage(gameMessage(EFFECT_FADED));

		verify(localPlayer, never()).setOverheadText(any());
	}

	@Test
	public void spendingTheBuffShowsNoOverheadText()
	{
		when(config.showOwnOverheadText()).thenReturn(true);
		when(config.wastedOverheadText()).thenReturn("Oh, behave!");

		plugin.onChatMessage(gameMessage(SELF_CAST));
		landMeleeHit(target);

		verify(localPlayer, never()).setOverheadText(any());
	}

	@Test
	public void receivingAnAllysBuffReportsAppliedToThem()
	{
		givenWeAreInAParty();

		plugin.onChatMessage(gameMessage(ALLY_CAST));

		EnticeStatusMessage sent = lastSentMessage(1);
		assertEquals(EnticeStatus.RECEIVED, sent.getStatus());
		assertEquals("Zezima", sent.getCasterName());
	}

	@Test
	public void anAllysNameInAColourCodeIsReportedPlain()
	{
		givenWeAreInAParty();

		// as seen live: the game colours the caster's name with a code that is not a tag
		plugin.onChatMessage(gameMessage(ALLY_CAST.replace("Zezima", "@mes_hl_blu@Zezima")));

		assertEquals("Zezima", lastSentMessage(1).getCasterName());
	}

	@Test
	public void blowingYourOwnHornReportsSentWithNoCaster()
	{
		givenWeAreInAParty();
		when(config.appliedOverheadText()).thenReturn("Buffed!");

		plugin.onChatMessage(gameMessage(SELF_CAST));

		EnticeStatusMessage sent = lastSentMessage(1);
		assertEquals(EnticeStatus.SENT, sent.getStatus());
	}

	@Test
	public void whiffingReportsItsOwnStatusAndLine()
	{
		givenWeAreInAParty();
		when(config.whiffOverheadText()).thenReturn("Nobody heard that...");

		plugin.onChatMessage(gameMessage(NO_LISTENERS));

		EnticeStatusMessage sent = lastSentMessage(1);
		assertEquals(EnticeStatus.MISSED, sent.getStatus());
		assertEquals("Nobody heard that...", sent.getOverheadText());
	}

	@Test
	public void whiffingWhileHoldingAnAllysBuffNamesNoCaster()
	{
		givenWeAreInAParty();

		plugin.onChatMessage(gameMessage(ALLY_CAST));
		plugin.onChatMessage(gameMessage(NO_LISTENERS));

		EnticeStatusMessage sent = lastSentMessage(2);
		assertEquals(EnticeStatus.MISSED, sent.getStatus());
		assertNull("the miss was our own horn", sent.getCasterName());
	}

	@Test
	public void whiffingStillShowsTextWhenItsSoundIsOff()
	{
		when(config.playWhiffSound()).thenReturn(false);
		when(config.showOwnOverheadText()).thenReturn(true);
		when(config.whiffOverheadText()).thenReturn("Nobody heard that...");

		plugin.onChatMessage(gameMessage(NO_LISTENERS));

		verify(localPlayer).setOverheadText("Nobody heard that...");
	}

	@Test
	public void aPartyMemberSeesTextForABuffTheyDidNotCast()
	{
		givenAnAllyReportedToUs();
		when(config.wastedOverheadText()).thenReturn("I wasted it!");

		// Cast by a third player, so we are simply another recipient watching it happen.
		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "SomeoneElse", "I wasted it!"));

		verify(allyPlayer).setOverheadText("I wasted it!");
		verifyNotPlayed(SoulflameHornAudioPlugin.ALLY_WASTED_SOUND);
	}

	@Test
	public void spendingAnAllysBuffReportsSpentToThem()
	{
		givenWeAreInAParty();

		plugin.onChatMessage(gameMessage(ALLY_CAST));
		landMeleeHit(target);

		assertEquals(EnticeStatus.SPENT, lastSentMessage(2).getStatus());
	}

	@Test
	public void wastingAnAllysBuffReportsWastedToThem()
	{
		givenWeAreInAParty();

		plugin.onChatMessage(gameMessage(ALLY_CAST));
		plugin.onChatMessage(gameMessage(EFFECT_FADED));

		assertEquals(EnticeStatus.WASTED, lastSentMessage(2).getStatus());
	}

	@Test
	public void yourOwnBuffIsReportedWithNoCasterAttached()
	{
		givenWeAreInAParty();

		plugin.onChatMessage(gameMessage(SELF_CAST));
		plugin.onChatMessage(gameMessage(EFFECT_FADED));

		EnticeStatusMessage sent = lastSentMessage(2);
		assertEquals(EnticeStatus.WASTED, sent.getStatus());
		assertNull("nobody else cast it, so no ally should be blamed", sent.getCasterName());
	}

	@Test
	public void mutingSilencesEveryEventButLeavesTextAlone()
	{
		when(config.enableSounds()).thenReturn(false);
		when(config.showOwnOverheadText()).thenReturn(true);
		when(config.wastedOverheadText()).thenReturn("I wasted it!");

		plugin.onChatMessage(gameMessage(SELF_CAST));
		plugin.onChatMessage(gameMessage(EFFECT_FADED));

		verifyNoInteractions(audioPlayer);
		verify(localPlayer).setOverheadText("I wasted it!");
	}

	@Test
	public void nothingIsReportedWhilePartyIntegrationIsOff()
	{
		when(config.partyIntegration()).thenReturn(false);
		when(partyService.isInParty()).thenReturn(true);

		plugin.onChatMessage(gameMessage(ALLY_CAST));
		plugin.onChatMessage(gameMessage(EFFECT_FADED));

		verify(partyService, never()).send(any());
	}

	@Test
	public void theSendersOwnLineTravelsWithTheReport()
	{
		givenWeAreInAParty();
		when(config.appliedOverheadText()).thenReturn("Buffed!");

		plugin.onChatMessage(gameMessage(ALLY_CAST));

		assertEquals("Buffed!", lastSentMessage(1).getOverheadText());
	}

	@Test
	public void anAllyWastingOurBuffPlaysTheAllySound()
	{
		givenAnAllyReportedToUs();

		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "Durial321", null));

		verifyPlayed(SoulflameHornAudioPlugin.ALLY_WASTED_SOUND);
	}

	@Test
	public void anAllyOnAnOlderVersionWastingOurBuffPlaysTheAllySound()
	{
		givenAnAllyReportedToUs();

		// an older version sends our name with the game's colour code still on it
		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "@mes_hl_blu@Durial321", null));

		verifyPlayed(SoulflameHornAudioPlugin.ALLY_WASTED_SOUND);
	}

	@Test
	public void aPartyReportWaitsForTheClientThread()
	{
		givenAnAllyReportedToUs();
		doAnswer(invocation -> null).when(clientThread).invokeLater(any(Runnable.class));

		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "Durial321", null));
		verifyNotPlayed(SoulflameHornAudioPlugin.ALLY_WASTED_SOUND);

		ArgumentCaptor<Runnable> queued = ArgumentCaptor.forClass(Runnable.class);
		verify(clientThread).invokeLater(queued.capture());
		queued.getValue().run();

		verifyPlayed(SoulflameHornAudioPlugin.ALLY_WASTED_SOUND);
	}

	@Test
	public void anAllySpendingOurBuffPlaysNoSound()
	{
		givenAnAllyReportedToUs();

		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.SPENT, "Durial321", null));

		verifyNotPlayed(SoulflameHornAudioPlugin.ALLY_WASTED_SOUND);
	}

	@Test
	public void everyStatusShowsAboveTheAllyWhoSentIt()
	{
		givenAnAllyReportedToUs();
		when(config.appliedOverheadText()).thenReturn("Buffed!");
		when(config.spentOverheadText()).thenReturn("Used it!");
		when(config.partyWastedOverheadText()).thenReturn("Wasted it!");

		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.RECEIVED, "Durial321", "theirs"));
		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.SPENT, "Durial321", "theirs"));
		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "Durial321", "theirs"));

		verify(allyPlayer).setOverheadText("Buffed!");
		verify(allyPlayer).setOverheadText("Used it!");
		verify(allyPlayer).setOverheadText("Wasted it!");
	}

	@Test
	public void ourOwnWordingIsUsedAboveAlliesByDefault()
	{
		givenAnAllyReportedToUs();
		when(config.partyWastedOverheadText()).thenReturn("They wasted it");

		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "Durial321", "Their wording"));

		verify(allyPlayer).setOverheadText("They wasted it");
	}

	@Test
	public void theirWordingIsUsedOnlyWhenOptedIn()
	{
		givenAnAllyReportedToUs();
		when(config.useOthersDialogue()).thenReturn(true);
		when(config.partyWastedOverheadText()).thenReturn("They wasted it");

		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "Durial321", "Their wording"));

		verify(allyPlayer).setOverheadText("Their wording");
	}

	@Test
	public void ourWordingCoversAnyoneWhoLeftTheirsBlank()
	{
		givenAnAllyReportedToUs();
		when(config.useOthersDialogue()).thenReturn(true);
		when(config.partyWastedOverheadText()).thenReturn("They wasted it");

		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "Durial321", "   "));

		verify(allyPlayer).setOverheadText("They wasted it");
	}

	@Test
	public void theAllyWastedSoundOnlyFiresForBuffsWeCast()
	{
		givenAnAllyReportedToUs();

		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "SomeoneElse", "Wasted it!"));

		verifyNotPlayed(SoulflameHornAudioPlugin.ALLY_WASTED_SOUND);
	}

	@Test
	public void ourOwnPartyBroadcastDoesNotEchoBack()
	{
		givenAnAllyReportedToUs();

		plugin.onEnticeStatusMessage(statusMessage(1L, EnticeStatus.WASTED, "Durial321", "Wasted it!"));

		verifyNotPlayed(SoulflameHornAudioPlugin.ALLY_WASTED_SOUND);
	}

	@Test
	public void anotherClientsMarkupIsStrippedBeforeItRenders()
	{
		givenAnAllyReportedToUs();
		when(config.useOthersDialogue()).thenReturn(true);

		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "Durial321", "<col=ff0000>oops</col>"));

		verify(allyPlayer).setOverheadText("oops");
	}

	@Test
	public void anOverlongOverheadMessageIsTruncated()
	{
		givenAnAllyReportedToUs();
		when(config.useOthersDialogue()).thenReturn(true);
		String tooLong = new String(new char[200]).replace('\0', 'x');

		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "Durial321", tooLong));

		ArgumentCaptor<String> shown = ArgumentCaptor.forClass(String.class);
		verify(allyPlayer).setOverheadText(shown.capture());
		assertEquals(80, shown.getValue().length());
	}

	@Test
	public void anAllyWhoSetNoLineShowsNothing()
	{
		givenAnAllyReportedToUs();
		when(config.useOthersDialogue()).thenReturn(true);

		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "Durial321", "   "));

		verify(allyPlayer, never()).setOverheadText(any());
	}

	private void givenWeAreInAParty()
	{
		when(config.partyIntegration()).thenReturn(true);
		when(partyService.isInParty()).thenReturn(true);
	}

	private void givenAnAllyReportedToUs()
	{
		when(config.partyIntegration()).thenReturn(true);
		when(config.showAllyOverheadText()).thenReturn(true);
		when(config.playAllyWastedSound()).thenReturn(true);
		when(localPlayer.getName()).thenReturn("Durial321");
		when(allyPlayer.getName()).thenReturn("Zezima");
		when(client.getTopLevelWorldView()).thenReturn(worldView);
		doReturn(players).when(worldView).players();
		when(players.iterator()).thenAnswer(invocation -> Arrays.asList(localPlayer, allyPlayer).iterator());
		when(partyService.getLocalMember()).thenReturn(new PartyMember(1L));

		PartyMember sender = new PartyMember(2L);
		sender.setDisplayName("Zezima");
		when(partyService.getMemberById(2L)).thenReturn(sender);
	}

	private EnticeStatusMessage lastSentMessage(int expectedCount)
	{
		ArgumentCaptor<EnticeStatusMessage> sent = ArgumentCaptor.forClass(EnticeStatusMessage.class);
		verify(partyService, times(expectedCount)).send(sent.capture());
		return sent.getAllValues().get(expectedCount - 1);
	}

	private static EnticeStatusMessage statusMessage(long memberId, EnticeStatus status, String casterName, String overheadText)
	{
		EnticeStatusMessage message = new EnticeStatusMessage(status, casterName, overheadText);
		message.setMemberId(memberId);
		return message;
	}

	@Test
	public void customFileNamesAreIgnoredUnlessCustomSoundsAreOn()
	{
		when(config.useCustomSounds()).thenReturn(false);
		when(config.sentFile()).thenReturn("yeah-baby");

		plugin.onChatMessage(gameMessage(SELF_CAST));

		verifyPlayed(SoulflameHornAudioPlugin.SENT_SOUND);
	}

	@Test
	public void afullPathToAClipIsPlayedDirectly() throws Exception
	{
		File clip = File.createTempFile("entice-test", ".wav");
		clip.deleteOnExit();
		when(config.useCustomSounds()).thenReturn(true);
		when(config.sentFile()).thenReturn(clip.getAbsolutePath());

		plugin.onChatMessage(gameMessage(SELF_CAST));

		verify(audioPlayer).play(eq(clip), anyFloat());
	}

	@Test
	public void anUnplayableFormatFallsBackRatherThanGoingSilent() throws Exception
	{
		File mp3 = File.createTempFile("entice-test", ".mp3");
		mp3.deleteOnExit();
		when(config.useCustomSounds()).thenReturn(true);
		when(config.sentFile()).thenReturn(mp3.getAbsolutePath());

		plugin.onChatMessage(gameMessage(SELF_CAST));

		verifyPlayed(SoulflameHornAudioPlugin.SENT_SOUND);
	}

	@Test
	public void aBlankCustomNameUsesTheBundledSound()
	{
		when(config.useCustomSounds()).thenReturn(true);
		when(config.sentFile()).thenReturn("");

		plugin.onChatMessage(gameMessage(SELF_CAST));

		verifyPlayed(SoulflameHornAudioPlugin.SENT_SOUND);
	}

	@Test
	public void anUnmatchedCustomNameFallsBackToTheBundledSound()
	{
		when(config.useCustomSounds()).thenReturn(true);
		when(config.sentFile()).thenReturn("a-name-with-no-matching-file-on-disk");

		plugin.onChatMessage(gameMessage(SELF_CAST));

		verifyPlayed(SoulflameHornAudioPlugin.SENT_SOUND);
	}

	@Test
	public void blowingTheHornYourselfPlaysTheSentSound()
	{
		plugin.onChatMessage(gameMessage(SELF_CAST));

		verifyPlayed(SoulflameHornAudioPlugin.SENT_SOUND);
	}

	@Test
	public void anAllyBlowingTheHornPlaysTheAppliedSound()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));

		verifyPlayed(SoulflameHornAudioPlugin.APPLIED_SOUND);
	}

	@Test
	public void landingAHitWhileEnticedPlaysTheConsumedSound()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		landMeleeHit(target);

		verifyPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void takingDamageWhileEnticedDoesNotSpendTheBuff()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		landMeleeHit(localPlayer);

		verifyNotPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void someoneElsesDamageDoesNotSpendTheBuff()
	{
		when(hitsplat.isMine()).thenReturn(false);

		plugin.onChatMessage(gameMessage(ALLY_CAST));
		landMeleeHit(target);

		verifyNotPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void theBuffExpiresAfterTenTicks()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		for (int i = 0; i < 10; i++)
		{
			nextTick();
		}
		landMeleeHit(target);

		verifyNotPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void theBuffSurvivesToTheTenthTick()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		for (int i = 0; i < 9; i++)
		{
			nextTick();
		}
		landMeleeHit(target);

		verifyPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void hittingWithoutTheBuffIsSilent()
	{
		landMeleeHit(target);

		verifyNoInteractions(audioPlayer);
	}

	@Test
	public void aWhiffedHornPlaysItsOwnSound()
	{
		when(config.playWhiffSound()).thenReturn(true);

		plugin.onChatMessage(gameMessage(NO_LISTENERS));

		verifyPlayed(SoulflameHornAudioPlugin.WHIFF_SOUND);
		verifyNotPlayed(SoulflameHornAudioPlugin.APPLIED_SOUND);
	}

	@Test
	public void aWhiffedHornIsSilentWhenTurnedOff()
	{
		when(config.playWhiffSound()).thenReturn(false);

		plugin.onChatMessage(gameMessage(NO_LISTENERS));

		verifyNoInteractions(audioPlayer);
	}

	@Test
	public void theWhiffSoundIsIndependentOfTheAppliedSound()
	{
		when(config.playAppliedSound()).thenReturn(false);
		when(config.playWhiffSound()).thenReturn(true);

		plugin.onChatMessage(gameMessage(NO_LISTENERS));

		verifyPlayed(SoulflameHornAudioPlugin.WHIFF_SOUND);
	}

	@Test
	public void aWhiffDoesNotArmTheBuff()
	{
		when(config.playWhiffSound()).thenReturn(true);

		plugin.onChatMessage(gameMessage(NO_LISTENERS));
		landMeleeHit(target);

		verifyNotPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void nonGameMessagesAreIgnored()
	{
		ChatMessage event = gameMessage(SELF_CAST);
		event.setType(ChatMessageType.PUBLICCHAT);

		plugin.onChatMessage(event);

		verifyNoInteractions(audioPlayer);
	}

	@Test
	public void zeroVolumeMutesPlayback()
	{
		when(config.volume()).thenReturn(0);

		plugin.onChatMessage(gameMessage(SELF_CAST));

		verifyNoInteractions(audioPlayer);
	}

	@Test
	public void aRangedHitLeavesTheBuffForTheNextMeleeOne()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		nextTick();
		gainXp(Skill.RANGED);
		landHit(target);

		verifyNotPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
		assertTrue("the buff is still waiting for a melee hit", plugin.isEnticeActive());

		gainXp(Skill.STRENGTH);
		landHit(target);

		verifyPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void aMagicHitDoesNotSpendTheBuff()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		nextTick();
		gainXp(Skill.MAGIC);
		landHit(target);

		verifyNotPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void longrangeDefenceXpIsNotMistakenForMelee()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		nextTick();
		gainXp(Skill.RANGED);
		gainXp(Skill.DEFENCE);
		landHit(target);

		verifyNotPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void aMeleeHitSpendsTheBuff()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		nextTick();
		gainXp(Skill.ATTACK);
		landHit(target);

		verifyPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void rangedXpFromBeforeTheHornDoesNotCount()
	{
		gainXp(Skill.RANGED);
		nextTick();
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		nextTick();
		landMeleeHit(target);

		verifyPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void aHitWithNoExperienceLikeAThrallsDoesNotSpendTheBuff()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		nextTick();
		landHit(target);

		verifyNotPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
		assertTrue("the buff is still waiting for a melee hit", plugin.isEnticeActive());
	}

	@Test
	public void meleeExperienceFromBeforeTheHornDoesNotCount()
	{
		gainXp(Skill.STRENGTH);
		nextTick();
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		nextTick();
		landHit(target);

		verifyNotPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void aThrallHitLongAfterAMeleeOneDoesNotSpendTheBuff()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		nextTick();
		gainXp(Skill.RANGED);
		gainXp(Skill.STRENGTH);
		nextTick();
		nextTick();
		nextTick();
		landHit(target);

		verifyNotPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void theFirstReadingOfASkillIsNotAGain()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		nextTick();
		// On login the client reports every skill once; that is where it stands, not experience gained.
		plugin.onStatChanged(new StatChanged(Skill.RANGED, 5_000_000, 99, 99));
		landMeleeHit(target);

		verifyPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void severalAlliesWastingOneBlowMakeOneSound()
	{
		givenAnAllyReportedToUs();
		plugin.onChatMessage(gameMessage(SELF_CAST));

		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "Durial321", null));
		plugin.onEnticeStatusMessage(statusMessage(3L, EnticeStatus.WASTED, "Durial321", null));
		plugin.onEnticeStatusMessage(statusMessage(4L, EnticeStatus.WASTED, "Durial321", null));

		verifyPlayedTimes(SoulflameHornAudioPlugin.ALLY_WASTED_SOUND, 1);
	}

	@Test
	public void ourOwnFadeAndAnAllysWasteMakeOneSound()
	{
		givenAnAllyReportedToUs();
		plugin.onChatMessage(gameMessage(SELF_CAST));

		plugin.onChatMessage(gameMessage(EFFECT_FADED));
		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "Durial321", null));

		verifyPlayedTimes(SoulflameHornAudioPlugin.FADED_SOUND, 1);
		verifyPlayedTimes(SoulflameHornAudioPlugin.ALLY_WASTED_SOUND, 0);
	}

	@Test
	public void theNextBlowCanMakeItsOwnWastedSound()
	{
		givenAnAllyReportedToUs();
		plugin.onChatMessage(gameMessage(SELF_CAST));
		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "Durial321", null));

		plugin.onChatMessage(gameMessage(SELF_CAST));
		plugin.onEnticeStatusMessage(statusMessage(2L, EnticeStatus.WASTED, "Durial321", null));

		verifyPlayedTimes(SoulflameHornAudioPlugin.ALLY_WASTED_SOUND, 2);
	}

	@Test
	public void aQuotedPathIsPlayed() throws Exception
	{
		File clip = File.createTempFile("entice-test", ".wav");
		clip.deleteOnExit();
		final String path = clip.getAbsolutePath();
		when(config.useCustomSounds()).thenReturn(true);

		for (String quoted : new String[]{"\"" + path + "\"", "'" + path + "'", "  \"" + path + "\"  "})
		{
			when(config.sentFile()).thenReturn(quoted);
			plugin.onChatMessage(gameMessage(SELF_CAST));
		}

		verify(audioPlayer, times(3)).play(eq(clip), anyFloat());
	}

	@Test
	public void quotesAroundASettingAreDropped()
	{
		assertEquals("entice-sent", SoulflameHornAudioPlugin.unquote("\"entice-sent\""));
		assertEquals("entice-sent", SoulflameHornAudioPlugin.unquote("'entice-sent'"));
		assertEquals("entice-sent", SoulflameHornAudioPlugin.unquote("  entice-sent  "));
		assertEquals("C:\\Sounds\\horn.wav", SoulflameHornAudioPlugin.unquote("\"C:\\Sounds\\horn.wav"));
		assertEquals("", SoulflameHornAudioPlugin.unquote("\"\""));
	}

	@Test
	public void onlyQuotesIsTreatedAsBlank()
	{
		when(config.useCustomSounds()).thenReturn(true);
		when(config.sentFile()).thenReturn("\"\"");

		plugin.onChatMessage(gameMessage(SELF_CAST));

		verifyPlayed(SoulflameHornAudioPlugin.SENT_SOUND);
	}

	private static ChatMessage gameMessage(String message)
	{
		ChatMessage event = new ChatMessage();
		event.setType(ChatMessageType.GAMEMESSAGE);
		event.setMessage(message);
		return event;
	}

	/** A hit landing this tick, settled as the tick ends, the way the client sees it. */
	private void landHit(Actor actor)
	{
		plugin.onHitsplatApplied(hitsplatOn(actor));
		nextTick();
	}

	private void landMeleeHit(Actor actor)
	{
		gainXp(Skill.STRENGTH);
		landHit(actor);
	}

	private void nextTick()
	{
		plugin.onGameTick(new GameTick());
		tick++;
	}

	/** Experience as the client reports it: the first sighting of a skill is its starting value. */
	private void gainXp(Skill skill)
	{
		final int base = 1_000_000;
		plugin.onStatChanged(new StatChanged(skill, base, 99, 99));
		plugin.onStatChanged(new StatChanged(skill, base + 100 + tick, 99, 99));
	}

	private HitsplatApplied hitsplatOn(Actor actor)
	{
		HitsplatApplied event = new HitsplatApplied();
		event.setActor(actor);
		event.setHitsplat(hitsplat);
		return event;
	}

	/**
	 * Custom sounds are off in these tests, so playback always resolves to the bundled resource
	 * regardless of what happens to be sitting in the user's real sound folder.
	 */
	private void verifyPlayed(String bundled)
	{
		try
		{
			verify(audioPlayer).play(any(), eq("/soulflameaudio/" + bundled + ".wav"), anyFloat());
		}
		catch (Exception e)
		{
			throw new AssertionError(e);
		}
	}

	private void verifyPlayedTimes(String bundled, int times)
	{
		try
		{
			verify(audioPlayer, times(times)).play(any(), eq("/soulflameaudio/" + bundled + ".wav"), anyFloat());
		}
		catch (Exception e)
		{
			throw new AssertionError(e);
		}
	}

	private void verifyNotPlayed(String bundled)
	{
		try
		{
			verify(audioPlayer, never()).play(any(), eq("/soulflameaudio/" + bundled + ".wav"), anyFloat());
			verify(audioPlayer, never()).play(any(File.class), anyFloat());
		}
		catch (Exception e)
		{
			throw new AssertionError(e);
		}
	}
}
