<#if data.total == 0>У вас пока нет мест. Добавьте первое — /add<#else><b>📢 Публикация в группы</b>
Выберите места (выбрано: ${data.selectedCount}), страница ${data.page}/${data.totalPages}:

<#list data.items as item>
${item.selected?then("☑", "⬜")} ${item.index}. ${item.name?html}
</#list>
</#if>