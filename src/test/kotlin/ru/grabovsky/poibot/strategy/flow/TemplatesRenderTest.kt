package ru.grabovsky.poibot.strategy.flow

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer
import ru.grabovsky.poibot.strategy.flow.addplace.*
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKeys
import ru.grabovsky.poibot.strategy.flow.core.templating.FlowTemplateRenderer
import ru.grabovsky.poibot.strategy.flow.nearby.*
import ru.grabovsky.poibot.strategy.flow.places.*
import ru.grabovsky.poibot.strategy.flow.start.StartViewModel
import java.util.*

/** Проверяет, что все шаблоны рендерятся на обоих языках и экранируют пользовательский ввод. */
class TemplatesRenderTest : ShouldSpec({
    val configurer = FreeMarkerConfigurer().apply {
        setTemplateLoaderPath("classpath:/message/template")
        setDefaultEncoding("UTF-8")
        afterPropertiesSet()
    }
    val renderer = FlowTemplateRenderer(configurer)
    val ru = Locale.forLanguageTag("ru")
    val en = Locale.forLanguageTag("en")

    should("escape HTML in user supplied name on the form") {
        val view = AddFormView(false, "<b>Bar & Grill</b>", null, true, false, null, null)

        val text = renderer.render(FlowKeys.ADD_PLACE, "form", ru, view)

        text shouldContain "&lt;b&gt;Bar &amp; Grill&lt;/b&gt;"
        text shouldNotContain "<b>Bar & Grill</b>"
    }

    should("render form in both languages") {
        val view = AddFormView(true, "Хмель", "Ленина 5", false, true, "https://khmel.ru", "Описание")

        renderer.render(FlowKeys.ADD_PLACE, "form", ru, view) shouldContain "Редактирование"
        renderer.render(FlowKeys.ADD_PLACE, "form", en, view) shouldContain "Edit place"
    }

    should("render every prompt field") {
        PlaceField.entries.forEach { field ->
            renderer.render(FlowKeys.ADD_PLACE, "prompt", ru, PromptView(field.code, false, null)).isNotBlank() shouldBe true
            renderer.render(FlowKeys.ADD_PLACE, "prompt", en, PromptView(field.code, false, "error")) shouldContain "error"
        }
    }

    should("render resolve screens") {
        val candidates = listOf(CandidateView("Хмель", "Ленина 5", "20 м"), CandidateView("Бар", null, "50 м"))

        renderer.render(FlowKeys.ADD_PLACE, "resolve", ru, ResolveView(true, candidates.take(1))) shouldContain "Это то же самое место?"
        renderer.render(FlowKeys.ADD_PLACE, "resolve", en, ResolveView(false, candidates)) shouldContain "Is it one of them?"
    }

    should("render saved and field choice") {
        renderer.render(FlowKeys.ADD_PLACE, "saved", ru, SavedView("Хмель", false)) shouldContain "сохранено"
        renderer.render(FlowKeys.ADD_PLACE, "saved", en, SavedView("Хмель", true)) shouldContain "updated"
        renderer.render(FlowKeys.ADD_PLACE, "field_choice", ru, FieldChoiceView("a < b")) shouldContain "a &lt; b"
    }

    should("render empty and non-empty places list") {
        renderer.render(FlowKeys.PLACES, "list", ru, PlacesListView(emptyList(), 1, 1, 0)) shouldContain "/add"
        val full = PlacesListView(listOf(ListItemView(1, "Хмель", "Ленина 5")), 1, 2, 9)
        renderer.render(FlowKeys.PLACES, "list", en, full) shouldContain "page 1/2"
    }

    should("render place card with optional fields") {
        val minimal = PlaceCardView("Хмель", null, null, null, null)
        val full = PlaceCardView("Хмель", "Ленина 5", "Описание", "https://khmel.ru", "120 м")

        renderer.render(FlowKeys.PLACES, "card", ru, minimal).trim() shouldBe "<b>Хмель</b>"
        renderer.render(FlowKeys.PLACES, "card", ru, full) shouldContain "120 м от вас"
        renderer.render(FlowKeys.PLACES, "card", en, full) shouldContain "120 м from you"
        renderer.render(FlowKeys.PLACES, "confirm_delete", ru, ConfirmDeleteView("Хмель")) shouldContain "Удалить"
    }

    should("render nearby results with hints only") {
        val view = NearbyView(
            radiusText = "100 м",
            points = emptyList(),
            hints = listOf(HintView("500 м", 3, 4), HintView("1 км", 2, 6)),
            nothingAnywhere = false,
            hiddenCount = 0,
        )

        val text = renderer.render(FlowKeys.NEARBY, "result", ru, view)

        text shouldContain "В радиусе <b>100 м</b> ничего нет"
        text shouldContain "до 500 м ещё +3 (всего 4)"
        text shouldContain "до 1 км ещё +2 (всего 6)"
        text shouldNotContain "250"
    }

    should("render nearby results with points and empty case") {
        val points = listOf(PointView(1, "Хмель", "80 м"), PointView(2, "Бар", "200 м"))
        val view = NearbyView("250 м", points, emptyList(), false, 3)

        val text = renderer.render(FlowKeys.NEARBY, "result", en, view)

        text shouldContain "Within 250 м"
        text shouldContain "1. <b>Хмель</b> — 80 м"
        text shouldContain "3 more"
        renderer.render(FlowKeys.NEARBY, "result", ru, NearbyView("250 м", emptyList(), emptyList(), true, 0)) shouldContain "ничего не найдено"
        renderer.render(FlowKeys.NEARBY, "wait", ru, NearbyFlow.WaitView("250 м")) shouldContain "250 м"
        renderer.render(FlowKeys.NEARBY, "radius", en, RadiusView("250 м")) shouldContain "250 м"
    }

    should("render start and help") {
        renderer.render(FlowKeys.START, "main", ru, StartViewModel("Алексей")) shouldContain "Алексей"
        renderer.render(FlowKeys.START, "main", en, StartViewModel("Alex")) shouldContain "Welcome"
        renderer.render(FlowKeys.HELP, "main", ru, null) shouldContain "/nearby"
        renderer.render(FlowKeys.HELP, "main", en, null) shouldContain "/places"
    }
})
