<b>💬 Комментарии</b> (${data.total?c})<#if data.rating??> · ⭐ ${data.rating}</#if>, страница ${data.page}/${data.totalPages}

<#list data.lines as line>
${line.index}. ${line.text?html}
<i>${line.date}</i><#if line.mine && data.manage> · <i>ваш</i></#if>

</#list>
<#if data.manage && data.lines?size gt 0>Авторы скрыты. 🚩 — пожаловаться на комментарий, 🗑 — удалить свой.</#if>
