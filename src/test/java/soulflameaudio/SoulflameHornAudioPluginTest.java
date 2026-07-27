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
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.audio.AudioPlayer;
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
	private Player localPlayer;

	@Mock
	private Player allyPlayer;

	@Mock
	private NPC target;

	@Mock
	private Hitsplat hitsplat;

	@Inject
	private SoulflameHornAudioPlugin plugin;

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
		plugin.onHitsplatApplied(hitsplatOn(target));

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
		plugin.onHitsplatApplied(hitsplatOn(target));

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
		plugin.onHitsplatApplied(hitsplatOn(target));

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
		when(client.getPlayers()).thenReturn(Arrays.asList(localPlayer, allyPlayer));
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
		plugin.onHitsplatApplied(hitsplatOn(target));

		verifyPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void takingDamageWhileEnticedDoesNotSpendTheBuff()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		plugin.onHitsplatApplied(hitsplatOn(localPlayer));

		verifyNotPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void someoneElsesDamageDoesNotSpendTheBuff()
	{
		when(hitsplat.isMine()).thenReturn(false);

		plugin.onChatMessage(gameMessage(ALLY_CAST));
		plugin.onHitsplatApplied(hitsplatOn(target));

		verifyNotPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void theBuffExpiresAfterTenTicks()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		for (int i = 0; i < 10; i++)
		{
			plugin.onGameTick(new GameTick());
		}
		plugin.onHitsplatApplied(hitsplatOn(target));

		verifyNotPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void theBuffSurvivesToTheTenthTick()
	{
		plugin.onChatMessage(gameMessage(ALLY_CAST));
		for (int i = 0; i < 9; i++)
		{
			plugin.onGameTick(new GameTick());
		}
		plugin.onHitsplatApplied(hitsplatOn(target));

		verifyPlayed(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void hittingWithoutTheBuffIsSilent()
	{
		plugin.onHitsplatApplied(hitsplatOn(target));

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
		plugin.onHitsplatApplied(hitsplatOn(target));

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

	private static ChatMessage gameMessage(String message)
	{
		ChatMessage event = new ChatMessage();
		event.setType(ChatMessageType.GAMEMESSAGE);
		event.setMessage(message);
		return event;
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
