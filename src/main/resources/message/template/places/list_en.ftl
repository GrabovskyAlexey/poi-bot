<#if data.total == 0>You have no saved places yet. Add the first one — /add<#else><b>📍 My places</b> (${data.total?c}), page ${data.page}/${data.totalPages}

<#list data.items as item>
${item.index}. <b>${item.name?html}</b><#if item.hasLocation> 🗺</#if><#if item.hasPhoto> 📷</#if><#if item.address??> — ${item.address?html}</#if>
</#list>
<i>🗺 — has a location, 📷 — has a photo</i>
</#if>