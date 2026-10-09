<#if data.total == 0>You have no places yet. Add the first one — /add<#else><b>📢 Publish to groups</b>
Choose places (selected: ${data.selectedCount}), page ${data.page}/${data.totalPages}:

<#list data.items as item>
${item.selected?then("☑", "⬜")} ${item.index}. ${item.name?html}<#if item.hasLocation> 🗺</#if><#if item.hasPhoto> 📷</#if>
</#list>
</#if>