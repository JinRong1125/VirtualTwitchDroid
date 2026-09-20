package com.example.virtualtwitchdroid.core.network.chat

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class IrcParserTest {

    @Test
    fun parseLine_splitsTagsPrefixCommandAndTrailingParam() {
        val line = "@color=#00FF00;display-name=Alice PRIVMSG #chan :hello world"
        val msg = IrcParser.parseLine(line)

        assertEquals("PRIVMSG", msg.command)
        assertEquals("#00FF00", msg.tags["color"])
        assertEquals("Alice", msg.tags["display-name"])
        // The trailing ":..." keeps spaces as a single param.
        assertEquals("hello world", msg.params.last())
    }

    @Test
    fun parseLine_parsesPrefixWhenPresent() {
        val line = ":alice!alice@alice.tmi.twitch.tv PRIVMSG #chan :hi"
        val msg = IrcParser.parseLine(line)

        assertEquals("alice!alice@alice.tmi.twitch.tv", msg.prefix)
        assertEquals("PRIVMSG", msg.command)
        assertEquals("hi", msg.params.last())
    }

    @Test
    fun parseLine_unescapesTagValues() {
        // \s -> space, \: -> ';'
        val line = "@system-msg=Hi\\sthere\\: PRIVMSG #chan :x"
        val msg = IrcParser.parseLine(line)

        assertEquals("Hi there;", msg.tags["system-msg"])
    }

    @Test
    fun toChatMessage_readsDisplayNameColorBadgesAndText() {
        val line = "@badges=moderator/1,subscriber/12;color=#FF0000;display-name=Bob;id=abc " +
            ":bob!bob@bob.tmi.twitch.tv PRIVMSG #chan :GG everyone"
        val chat = IrcParser.toChatMessage(IrcParser.parseLine(line))

        assertEquals("Bob", chat.userName)
        assertEquals("#FF0000", chat.color)
        assertEquals("GG everyone", chat.message)
        assertEquals("abc", chat.id)
        assertEquals(listOf("moderator", "subscriber"), chat.badges)
        assertFalse(chat.isAction)
    }

    @Test
    fun toChatMessage_detectsAndUnwrapsActionMessages() {
        val line = "@display-name=Carol PRIVMSG #chan :ACTION waves hello"
        val chat = IrcParser.toChatMessage(IrcParser.parseLine(line))

        assertTrue(chat.isAction)
        assertEquals("waves hello", chat.message)
    }

    @Test
    fun toChatMessage_fallsBackToPrefixLoginWhenNoDisplayName() {
        val line = ":dave!dave@dave.tmi.twitch.tv PRIVMSG #chan :yo"
        val chat = IrcParser.toChatMessage(IrcParser.parseLine(line))

        assertEquals("dave", chat.userName)
        assertNull(chat.color)
    }

    @Test
    fun toChatMessage_blankColorBecomesNull() {
        val line = "@color=;display-name=Erin PRIVMSG #chan :hey"
        val chat = IrcParser.toChatMessage(IrcParser.parseLine(line))

        assertNull(chat.color)
        assertEquals("Erin", chat.userName)
    }

    @Test
    fun toChatMessage_parsesEmotesTagIntoPositions() {
        // "Kappa Kappa" -> two Kappa (id 25) at code-point ranges 0-4 and 6-10.
        val line = "@emotes=25:0-4,6-10;display-name=Al PRIVMSG #chan :Kappa Kappa"
        val chat = IrcParser.toChatMessage(IrcParser.parseLine(line))

        assertEquals(2, chat.emotes.size)
        assertEquals("25", chat.emotes[0].id)
        assertEquals(0, chat.emotes[0].begin)
        assertEquals(4, chat.emotes[0].end)
        assertEquals("25", chat.emotes[1].id)
        assertEquals(6, chat.emotes[1].begin)
        assertEquals(10, chat.emotes[1].end)
    }

    @Test
    fun toChatMessage_parsesMultipleEmoteIds() {
        // "Kappa 4Head": Kappa (25) at 0-4, 4Head (354) at 6-10.
        val line = "@emotes=25:0-4/354:6-10;display-name=Al PRIVMSG #chan :Kappa 4Head"
        val chat = IrcParser.toChatMessage(IrcParser.parseLine(line))

        assertEquals(setOf("25", "354"), chat.emotes.map { it.id }.toSet())
        val head = chat.emotes.first { it.id == "354" }
        assertEquals(6, head.begin)
        assertEquals(10, head.end)
    }

    @Test
    fun toChatMessage_noEmotesTag_isEmpty() {
        val line = "@display-name=Al PRIVMSG #chan :just text"
        val chat = IrcParser.toChatMessage(IrcParser.parseLine(line))
        assertTrue(chat.emotes.isEmpty())
    }

    @Test
    fun toChatMessage_emptyEmotesTag_isEmpty() {
        val line = "@emotes=;display-name=Al PRIVMSG #chan :hi"
        val chat = IrcParser.toChatMessage(IrcParser.parseLine(line))
        assertTrue(chat.emotes.isEmpty())
    }

    @Test
    fun toChatMessage_actionEmotePositionsAreOffsetByActionPrefix() {
        // In an ACTION, Twitch counts positions from the raw "\u0001ACTION " prefix (8 code points).
        // Message becomes "Kappa"; the emote at raw 8-12 must map back to 0-4.
        val line = "@emotes=25:8-12;display-name=Al PRIVMSG #chan :ACTION Kappa"
        val chat = IrcParser.toChatMessage(IrcParser.parseLine(line))

        assertTrue(chat.isAction)
        assertEquals("Kappa", chat.message)
        assertEquals(1, chat.emotes.size)
        assertEquals(0, chat.emotes[0].begin)
        assertEquals(4, chat.emotes[0].end)
    }
}
