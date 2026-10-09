<#if data.total == 0>No places have been published in this chat yet. Add a place in a private chat with the bot (/add) and publish it to the group: /places → place → "To groups".<#else><b>📍 Chat places</b> (${data.total?c}), page ${data.page}/${data.totalPages}

<#list data.items as item>
${item.index}. <b>${item.name?html}</b><#if item.hasLocation> 🗺</#if><#if item.hasPhoto> 📷</#if><#if item.address??> — ${item.address?html}</#if>
</#list>
<i>🗺 — has a location, 📷 — has a photo</i>
</#if>