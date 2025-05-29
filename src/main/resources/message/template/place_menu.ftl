<#if (data.poi)?? && data.poi.isNotEmpty() >
<#if data.poi.name??>Название: ${data.poi.name}
</#if><#if data.poi.description??>Описание: ${data.poi.description}
</#if><#if data.poi.address??>Адрес: ${data.poi.address}
</#if>Геопозиция: <#if data.poi.location??>✅<#else>❌</#if>
<#else>
Введите данные точки
</#if>