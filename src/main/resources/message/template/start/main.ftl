<#assign lang = (locale.language!"")?lower_case>
<#if lang?starts_with("en")>
    <#include "main_en.ftl">
<#else>
    <#include "main_ru.ftl">
</#if>
