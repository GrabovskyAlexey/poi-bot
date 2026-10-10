<#if data.total == 0 && !data.filtered>You have no saved places yet. Add the first one — /add<#else><b>📍 My places</b> (${data.total?c}), page ${data.page}/${data.totalPages}<#if data.sortName??> · ${data.sortName}</#if>
<#if data.filtered>🔎 <#if data.query??>«${data.query?html}» </#if><#if data.withLocation>🗺 </#if><#if data.withPhoto>📷</#if>
</#if>

<#if data.total == 0>Nothing found. Change the conditions or reset them.<#else><#list data.items as item>${item.index}. <b>${item.name?html}</b><#if item.rating??> ⭐ ${item.rating}</#if><#if item.hasLocation> 🗺</#if><#if item.hasPhoto> 📷</#if><#if item.address??> — ${item.address?html}</#if>
</#list>
<i>🗺 — has a location, 📷 — has a photo</i></#if>
</#if>
