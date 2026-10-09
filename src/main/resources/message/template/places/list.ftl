<#if data.total == 0>У вас пока нет сохранённых мест. Добавьте первое — /add<#else><b>📍 Мои места</b> (${data.total?c}), страница ${data.page}/${data.totalPages}

<#list data.items as item>
${item.index}. <b>${item.name?html}</b><#if item.address??> — ${item.address?html}</#if>
</#list>
</#if>