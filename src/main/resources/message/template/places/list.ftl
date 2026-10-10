<#if data.total == 0 && !data.filtered>У вас пока нет сохранённых мест. Добавьте первое — /add<#else><b>📍 Мои места</b> (${data.total?c}), страница ${data.page}/${data.totalPages}<#if data.sortName??> · ${data.sortName}</#if>
<#if data.filtered>🔎 <#if data.query??>«${data.query?html}» </#if><#if data.tag??>🏷 #${data.tag?html} </#if><#if data.statusText??>${data.statusText} </#if><#if data.withLocation>🗺 </#if><#if data.withPhoto>📷</#if>
</#if>

<#if data.total == 0>Ничего не найдено. Измените условия или сбросьте их.<#else><#list data.items as item>${item.index}. <b>${item.name?html}</b><#if item.rating??> ⭐ ${item.rating}</#if><#if item.hasLocation> 🗺</#if><#if item.hasPhoto> 📷</#if><#if item.address??> — ${item.address?html}</#if>
</#list>
<i>🗺 — есть геопозиция, 📷 — есть фото</i></#if>
</#if>
