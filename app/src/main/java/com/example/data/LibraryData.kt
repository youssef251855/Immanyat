package com.example.data

import com.example.data.library.AbrogationArticles
import com.example.data.library.AllahArticles
import com.example.data.library.AngelsArticles
import com.example.data.library.CompanionsStoriesArticles
import com.example.data.library.FemaleCompanionsArticles
import com.example.data.library.GateOfKnowledgeArticles
import com.example.data.library.HadithQudsiArticles
import com.example.data.library.LeadersArticles
import com.example.data.library.LessonsStoriesArticles
import com.example.data.library.MajorSignsArticles
import com.example.data.library.MiracleQuranArticles
import com.example.data.library.ProphetBattlesArticles
import com.example.data.library.ProphetFeaturesArticles
import com.example.data.library.ProphetMedicineArticles
import com.example.data.library.ProphetWillsArticles
import com.example.data.library.ProphetsStoriesArticles
import com.example.data.library.QuranStoriesArticles
import com.example.data.library.TabiunStoriesArticles
import java.util.Locale

data class LibraryArticle(
    val id: String,
    val title: String,
    val content: String,
    val source: String = "المصادر الإسلامية المعتمدة"
)

data class LibraryCategory(
    val id: String,
    val name: String,
    val count: Int,
    val articles: List<LibraryArticle>
)

object LibraryData {
    val categories: List<LibraryCategory> = listOf(
        LibraryCategory(
            id = "allah",
            name = "الله - عز وجل",
            count = AllahArticles.articles.size,
            articles = AllahArticles.articles
        ),
        LibraryCategory(
            id = "angels",
            name = "الملائكة",
            count = AngelsArticles.articles.size,
            articles = AngelsArticles.articles
        ),
        LibraryCategory(
            id = "miracle_quran",
            name = "إعجاز القرآن",
            count = MiracleQuranArticles.articles.size,
            articles = MiracleQuranArticles.articles
        ),
        LibraryCategory(
            id = "hadith_qudsi",
            name = "الأحاديث القدسية",
            count = HadithQudsiArticles.articles.size,
            articles = HadithQudsiArticles.articles
        ),
        LibraryCategory(
            id = "prophetic_hadiths",
            name = "الأحاديث النبوية (٥٠ حديثاً بالمصادر)",
            count = HadithCollection.ALL_50_HADITHS.size,
            articles = HadithCollection.toLibraryArticles()
        ),
        LibraryCategory(
            id = "prophet_features",
            name = "شمائل الرسول",
            count = ProphetFeaturesArticles.articles.size,
            articles = ProphetFeaturesArticles.articles
        ),
        LibraryCategory(
            id = "prophet_battles",
            name = "غزوات الرسول",
            count = ProphetBattlesArticles.articles.size,
            articles = ProphetBattlesArticles.articles
        ),
        LibraryCategory(
            id = "prophet_wills",
            name = "من وصايا الرسول",
            count = ProphetWillsArticles.articles.size,
            articles = ProphetWillsArticles.articles
        ),
        LibraryCategory(
            id = "quran_stories",
            name = "قصص القرآن",
            count = QuranStoriesArticles.articles.size,
            articles = QuranStoriesArticles.articles
        ),
        LibraryCategory(
            id = "prophets_stories",
            name = "قصص الأنبياء",
            count = ProphetsStoriesArticles.articles.size,
            articles = ProphetsStoriesArticles.articles
        ),
        LibraryCategory(
            id = "companions_stories",
            name = "قصص الصحابة",
            count = CompanionsStoriesArticles.articles.size,
            articles = CompanionsStoriesArticles.articles
        ),
        LibraryCategory(
            id = "female_companions_stories",
            name = "قصص الصحابيات",
            count = FemaleCompanionsArticles.articles.size,
            articles = FemaleCompanionsArticles.articles
        ),
        LibraryCategory(
            id = "tabiun_stories",
            name = "قصص التابعين",
            count = TabiunStoriesArticles.articles.size,
            articles = TabiunStoriesArticles.articles
        ),
        LibraryCategory(
            id = "lessons_stories",
            name = "قصص وعبر",
            count = LessonsStoriesArticles.articles.size,
            articles = LessonsStoriesArticles.articles
        ),
        LibraryCategory(
            id = "leaders",
            name = "خلفاء وقادة",
            count = LeadersArticles.articles.size,
            articles = LeadersArticles.articles
        ),
        LibraryCategory(
            id = "abrogation",
            name = "الناسخ والمنسوخ",
            count = AbrogationArticles.articles.size,
            articles = AbrogationArticles.articles
        ),
        LibraryCategory(
            id = "major_signs",
            name = "علامات الساعة الكبرى",
            count = MajorSignsArticles.articles.size,
            articles = MajorSignsArticles.articles
        ),
        LibraryCategory(
            id = "gate_of_knowledge",
            name = "باب المعرفة",
            count = GateOfKnowledgeArticles.articles.size,
            articles = GateOfKnowledgeArticles.articles
        ),
        LibraryCategory(
            id = "prophet_medicine",
            name = "الطب النبوي",
            count = ProphetMedicineArticles.articles.size,
            articles = ProphetMedicineArticles.articles
        )
    )

    fun searchLibrary(query: String): List<LibraryCategory> {
        if (query.isBlank()) return categories
        val trimmedQuery = query.trim().lowercase(Locale.getDefault())
        return categories.mapNotNull { category ->
            val nameMatches = category.name.lowercase(Locale.getDefault()).contains(trimmedQuery)
            val matchedArticles = category.articles.filter { article ->
                article.title.lowercase(Locale.getDefault()).contains(trimmedQuery) ||
                    article.content.lowercase(Locale.getDefault()).contains(trimmedQuery) ||
                    article.source.lowercase(Locale.getDefault()).contains(trimmedQuery)
            }
            val resultArticles = when {
                matchedArticles.isNotEmpty() -> matchedArticles
                nameMatches -> category.articles
                else -> emptyList()
            }
            if (resultArticles.isNotEmpty()) {
                category.copy(
                    count = resultArticles.size,
                    articles = resultArticles
                )
            } else {
                null
            }
        }
    }
}
