<#if data.editing><b>✏️ Редактирование места</b><#else><b>➕ Новое место</b></#if>

<b>Название:</b> <#if data.name??>${data.name?html}<#else>—</#if>
<b>Адрес:</b> <#if data.address??>${data.address?html}<#else>—</#if>
<b>Геолокация:</b> <#if data.hasLocation>✅<#else>—</#if>
<b>Фото:</b> <#if data.hasPhoto>✅<#else>—</#if>
<b>Сайт:</b> <#if data.website??>${data.website?html}<#else>—</#if>
<b>Описание:</b> <#if data.description??>${data.description?html}<#else>—</#if>
<b>Теги:</b> <#if data.tags?has_content><#list data.tags as tag>#${tag?html}<#sep> </#list><#else>—</#if>
<b>Заметка:</b> <#if data.note??>${data.note?html}<#else>—</#if>
<b>Статус:</b> <#if data.statusText??>${data.statusText}<#else>—</#if>

<i>Поля можно заполнять в любом порядке: нажимайте кнопки или просто присылайте боту геопозицию, фото, место из карты (venue), ссылку или текст. Обязательно только название.</i>
