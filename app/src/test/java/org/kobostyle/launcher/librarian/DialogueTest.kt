package org.kobostyle.launcher.librarian

import org.junit.Assert.*
import org.junit.Test

class DialogueTest {
    @Test fun fixedFlowAndBackNeverDispatchNetworkUntilExplicitRequest() {
        var state = DialogueState()
        for ((action, value, stage) in listOf(
            Triple(DialogueAction.CHOOSE, "", DialogueStage.MOOD), Triple(DialogueAction.MOOD, "relax", DialogueStage.DURATION),
            Triple(DialogueAction.DURATION, "15", DialogueStage.PREFERENCE), Triple(DialogueAction.PREFERENCE, "yes", DialogueStage.CANDIDATES))) {
            val next = DialogueMachine.reduce(state, action, value)
            assertEquals(stage, next.state.stage); assertNull(next.effect); state = next.state
        }
        assertEquals(DialogueStage.PREFERENCE, DialogueMachine.reduce(state, DialogueAction.BACK).state.stage)
        val request = DialogueMachine.reduce(state, DialogueAction.REQUEST)
        assertEquals(DialogueStage.LOADING, request.state.stage)
        assertEquals(DialogueEffect.Recommend(true), request.effect)
        assertEquals(DialogueEffect.Cancel, DialogueMachine.reduce(request.state, DialogueAction.CANCEL).effect)
    }
    @Test fun sixExpressionsAndUnknownOrOutOfStateValuesDoNotEscape() {
        assertEquals(CatExpression.entries.toSet(), DialogueStage.entries.map { DialogueState(stage = it).expression }.toSet())
        assertEquals(6, CatExpression.entries.size)
        val mood = DialogueState(stage = DialogueStage.MOOD)
        assertEquals(Transition(mood), DialogueMachine.reduce(mood, DialogueAction.MOOD, "shell:reboot"))
        assertEquals(Transition(mood), DialogueMachine.reduce(mood, DialogueAction.DURATION, "30"))
        val duration = DialogueState(stage = DialogueStage.DURATION)
        assertEquals(Transition(duration), DialogueMachine.reduce(duration, DialogueAction.DURATION, "999"))
    }
    @Test fun openingRequiresConfirmationAndCurrentLocalId() {
        val result = LocalCurator.offline(listOf(sampleBook(1)), Choice())
        for (stage in DialogueStage.entries) {
            val state = DialogueState(stage = stage, result = result)
            assertNull(DialogueMachine.reduce(state, DialogueAction.OPEN, "invented").effect)
            if (stage != DialogueStage.CONFIRM) assertNull(DialogueMachine.reduce(state, DialogueAction.OPEN, "private-document-1").effect)
        }
        val state = DialogueState(stage = DialogueStage.CONFIRM, result = result)
        assertEquals(DialogueEffect.Open("private-document-1", false), DialogueMachine.reduce(state, DialogueAction.OPEN, "private-document-1").effect)
        assertEquals(DialogueEffect.Open("private-document-1", true), DialogueMachine.reduce(state, DialogueAction.OTHER_READER, "private-document-1").effect)
    }
    @Test fun textBoundaryResetAndHistoryLimit() {
        val initial = DialogueState()
        assertEquals(DialogueStage.CANDIDATES, DialogueMachine.reduce(initial, DialogueAction.QUERY, "書".repeat(200)).state.stage)
        assertEquals(initial, DialogueMachine.reduce(initial, DialogueAction.QUERY, "書".repeat(201)).state)
        assertEquals(initial, DialogueMachine.reduce(initial, DialogueAction.QUERY, " ").state)
        var state = initial
        repeat(10) { state = DialogueMachine.reduce(DialogueMachine.reduce(state, DialogueAction.CHOOSE).state, DialogueAction.BACK).state }
        assertEquals(6, state.history.size)
        assertEquals(DialogueState(), DialogueMachine.reduce(state, DialogueAction.RESET).state)
    }
    @Test fun offlineChoiceIsPreservedByMoreAndCardNavigationDoesNotCallApi() {
        val initial = DialogueState(stage = DialogueStage.CANDIDATES)
        val loading = DialogueMachine.reduce(initial, DialogueAction.OFFLINE).state
        val cards = loading.copy(stage = DialogueStage.CARDS, result = LocalCurator.offline((1..3).map(::sampleBook), Choice()))
        assertNull(DialogueMachine.reduce(cards, DialogueAction.NEXT).effect)
        assertEquals(1, DialogueMachine.reduce(cards, DialogueAction.NEXT).state.index)
        assertEquals(DialogueEffect.Recommend(false), DialogueMachine.reduce(cards, DialogueAction.MORE).effect)
        assertEquals(0, DialogueMachine.reduce(cards, DialogueAction.PREVIOUS).state.index)
    }
    @Test fun localRankingUsesMetadataNotInventedProgressAndSkipsUnavailable() {
        val unavailable = sampleBook(1).copy(scanStatus = "unavailable")
        val opened = sampleBook(2).copy(lastOpenedAt = 42)
        val psychology = sampleBook(3).copy(displayName = "[心理]-合成心理-作者.pdf")
        val ranked = LocalCurator.rank(listOf(unavailable, opened, psychology), Choice(mood = "deep"))
        assertEquals(listOf(psychology, opened), ranked)
        assertEquals(2, LocalCurator.offline(ranked, Choice(minutes = 15)).cards.size)
        assertTrue(LocalCurator.offline(ranked, Choice(minutes = 15)).cards.all { it.tip.contains("15") && it.tip.contains("不是讀完全書") })
        assertTrue(LocalCurator.offline(emptyList(), Choice()).cards.isEmpty())
    }
    @Test fun largeLibraryProducesBoundedCandidatesAndMoreRotates() {
        val books = (0 until 8166).map(::sampleBook)
        val first = LocalCurator.rank(books, Choice()).take(20)
        val next = LocalCurator.rank(books, Choice(round = 1)).take(20)
        assertEquals(20, first.size)
        assertNotEquals(first.first().identity, next.first().identity)
        assertTrue(next.all { it in books })
    }
    @Test fun naturalLanguageCategoryAndAuthorRequestsInfluenceLocalRecall() {
        val psychology = sampleBook(9).copy(displayName = "[心理]-測試心理-合成甲.pdf")
        val novel = sampleBook(1).copy(displayName = "[小說]-測試小說-合成乙.epub")
        val books = listOf(novel, psychology)
        assertEquals(psychology, LocalCurator.rank(books, Choice(query = "我今天想讀一點心理方面的書")).first())
        assertEquals(psychology, LocalCurator.rank(books, Choice(query = "請找合成甲寫的書")).first())
        assertEquals(novel, LocalCurator.rank(books, Choice(query = "測試小說 合成乙")).first())
    }
}
