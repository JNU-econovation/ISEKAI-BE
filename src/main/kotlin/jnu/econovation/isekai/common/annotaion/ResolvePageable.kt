package jnu.econovation.isekai.common.annotaion

import jnu.econovation.isekai.common.constant.SortField

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ResolvePageable(val allowed: Array<SortField>)