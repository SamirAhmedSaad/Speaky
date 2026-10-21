package com.speakmind.app.feature.course.di

import com.speakmind.app.feature.course.data.CourseRepository
import com.speakmind.app.feature.course.ui.CourseOutlineViewModel
import com.speakmind.app.feature.course.ui.CoursesHomeViewModel
import com.speakmind.app.feature.course.ui.ExamViewModel
import com.speakmind.app.feature.course.ui.LessonViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val courseModule = module {
    single { CourseRepository(get(), get(), get()) }
    viewModel { CoursesHomeViewModel(get(), get()) }
    viewModel { params -> CourseOutlineViewModel(params.get(), get(), get()) }
    viewModel { params -> LessonViewModel(params.get(), get(), get()) }
    viewModel { params -> ExamViewModel(params.get(), get(), get()) }
}
