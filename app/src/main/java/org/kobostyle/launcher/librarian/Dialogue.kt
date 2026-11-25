package org.kobostyle.launcher.librarian

import org.kobostyle.launcher.data.db.Book

enum class CatExpression { WELCOME, THINKING, RECOMMEND, CURIOUS, HAPPY, READING }
enum class DialogueStage { WELCOME, MOOD, DURATION, PREFERENCE, THEMES, CANDIDATES, LOADING, CARDS, DETAIL, CONFIRM }
enum class DialogueAction { CHOOSE, MOOD, DURATION, PREFERENCE, THEMES, THEME, TEXT, QUERY, REQUEST, OFFLINE, NEXT, PREVIOUS, VIEW, CONFIRM, OPEN, OTHER_READER, MORE, BACK, RESET, CANCEL, LIBRARY }
data class Choice(val mood: String = "surprise", val minutes: Int = 30, val preferUnopened: Boolean = true, val query: String = "", val round: Int = 0)
data class CuratedBook(val book: Book, val reason: String, val tip: String, val origin: String = "offline")
data class CuratorResult(val cards: List<CuratedBook>, val dialogue: String, val source: String, val notice: String = "", val inputTokens: Int = 0, val outputTokens: Int = 0,
    val indexedBooks: Int? = null, val checkedCandidates: Int = 0)
data class DialogueState(
    val stage: DialogueStage = DialogueStage.WELCOME,
    val choice: Choice = Choice(),
    val result: CuratorResult? = null,
    val index: Int = 0,
    val onlineRequest: Boolean = false,
    val history: List<String> = emptyList()
) {
    val card get() = result?.cards?.getOrNull(index)
    val expression get() = when (stage) {
        DialogueStage.WELCOME -> CatExpression.WELCOME
        DialogueStage.MOOD, DialogueStage.THEMES -> CatExpression.CURIOUS
        DialogueStage.DURATION, DialogueStage.PREFERENCE, DialogueStage.CANDIDATES, DialogueStage.LOADING -> CatExpression.THINKING
        DialogueStage.CARDS -> CatExpression.RECOMMEND
        DialogueStage.DETAIL -> CatExpression.READING
        DialogueStage.CONFIRM -> CatExpression.HAPPY
    }
}
sealed interface DialogueEffect {
    data class Recommend(val online: Boolean) : DialogueEffect
    data class Open(val id: String, val chooser: Boolean) : DialogueEffect
    data object Library : DialogueEffect
    data object TextEntry : DialogueEffect
    data object Cancel : DialogueEffect
}
data class Transition(val state: DialogueState, val effect: DialogueEffect? = null)

/** Only local UI actions reach this reducer. Model suggestions are never dispatched here. */
object DialogueMachine {
    fun reduce(s: DialogueState, action: DialogueAction, value: String = ""): Transition {
        fun changed(next: DialogueState, effect: DialogueEffect? = null) = Transition(
            next.copy(history = (s.history + action.name).takeLast(6)), effect)
        if (action == DialogueAction.RESET) return Transition(DialogueState(), DialogueEffect.Cancel)
        if (action == DialogueAction.BACK) {
            val previous = when (s.stage) {
                DialogueStage.WELCOME -> return Transition(s)
                DialogueStage.MOOD, DialogueStage.THEMES -> DialogueStage.WELCOME
                DialogueStage.DURATION -> DialogueStage.MOOD
                DialogueStage.PREFERENCE -> DialogueStage.DURATION
                DialogueStage.CANDIDATES -> if (s.choice.query.isNotBlank()) DialogueStage.WELCOME else DialogueStage.PREFERENCE
                DialogueStage.LOADING, DialogueStage.CARDS -> DialogueStage.CANDIDATES
                DialogueStage.DETAIL -> DialogueStage.CARDS
                DialogueStage.CONFIRM -> DialogueStage.DETAIL
            }
            return changed(s.copy(stage = previous), if (s.stage == DialogueStage.LOADING) DialogueEffect.Cancel else null)
        }
        return when {
            action == DialogueAction.LIBRARY && s.stage != DialogueStage.LOADING -> Transition(s, DialogueEffect.Library)
            action == DialogueAction.CHOOSE && s.stage == DialogueStage.WELCOME -> changed(s.copy(stage = DialogueStage.MOOD, choice = Choice()))
            action == DialogueAction.THEMES && s.stage == DialogueStage.WELCOME -> changed(s.copy(stage = DialogueStage.THEMES))
            action == DialogueAction.THEME && s.stage == DialogueStage.THEMES && value in setOf("relax", "learn", "surprise") ->
                changed(s.copy(stage = DialogueStage.CANDIDATES, choice = Choice(mood = value)))
            action == DialogueAction.TEXT && s.stage in setOf(DialogueStage.WELCOME, DialogueStage.THEMES) -> Transition(s, DialogueEffect.TextEntry)
            action == DialogueAction.QUERY && s.stage in setOf(DialogueStage.WELCOME, DialogueStage.THEMES) && value.isNotBlank() && value.codePointCount(0, value.length) <= 200 ->
                changed(s.copy(stage = DialogueStage.CANDIDATES, choice = Choice(query = value.trim())))
            action == DialogueAction.MOOD && s.stage == DialogueStage.MOOD && value in setOf("relax", "learn", "deep", "surprise") ->
                changed(s.copy(stage = DialogueStage.DURATION, choice = s.choice.copy(mood = value)))
            action == DialogueAction.DURATION && s.stage == DialogueStage.DURATION && value in setOf("15", "30", "60") ->
                changed(s.copy(stage = DialogueStage.PREFERENCE, choice = s.choice.copy(minutes = value.toInt())))
            action == DialogueAction.PREFERENCE && s.stage == DialogueStage.PREFERENCE && value in setOf("yes", "no") ->
                changed(s.copy(stage = DialogueStage.CANDIDATES, choice = s.choice.copy(preferUnopened = value == "yes")))
            action in setOf(DialogueAction.REQUEST, DialogueAction.OFFLINE) && s.stage == DialogueStage.CANDIDATES ->
                changed(s.copy(stage = DialogueStage.LOADING, onlineRequest = action == DialogueAction.REQUEST), DialogueEffect.Recommend(action == DialogueAction.REQUEST))
            action == DialogueAction.MORE && s.stage == DialogueStage.CARDS ->
                changed(s.copy(stage = DialogueStage.LOADING, choice = s.choice.copy(round = s.choice.round + 1)), DialogueEffect.Recommend(s.onlineRequest))
            action == DialogueAction.CANCEL && s.stage == DialogueStage.LOADING -> changed(s.copy(stage = DialogueStage.CANDIDATES), DialogueEffect.Cancel)
            action == DialogueAction.NEXT && s.stage == DialogueStage.CARDS && s.index + 1 < (s.result?.cards?.size ?: 0) -> changed(s.copy(index = s.index + 1))
            action == DialogueAction.PREVIOUS && s.stage == DialogueStage.CARDS && s.index > 0 -> changed(s.copy(index = s.index - 1))
            action == DialogueAction.VIEW && s.stage == DialogueStage.CARDS && s.card?.book?.identity == value -> changed(s.copy(stage = DialogueStage.DETAIL))
            action == DialogueAction.CONFIRM && s.stage == DialogueStage.DETAIL && s.card != null -> changed(s.copy(stage = DialogueStage.CONFIRM))
            action in setOf(DialogueAction.OPEN, DialogueAction.OTHER_READER) && s.stage == DialogueStage.CONFIRM && s.card?.book?.identity == value ->
                Transition(s, DialogueEffect.Open(value, action == DialogueAction.OTHER_READER))
            else -> Transition(s)
        }
    }
}
