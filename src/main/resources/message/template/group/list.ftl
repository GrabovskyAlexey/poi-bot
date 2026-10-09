<#if data.total == 0>В этом чате пока нет опубликованных мест. Добавьте место в личке с ботом (/add) и опубликуйте его в группу: /places → место → «В группы».<#else><b>📍 Места чата</b> (${data.total?c}), страница ${data.page}/${data.totalPages}

<#list data.items as item>
${item.index}. <b>${item.name?html}</b><#if item.address??> — ${item.address?html}</#if>
</#list>
</#if>