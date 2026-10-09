<b>💬 Comments</b> (${data.total?c})<#if data.rating??> · ⭐ ${data.rating}</#if>, page ${data.page}/${data.totalPages}

<#list data.lines as line>
${line.index}. ${line.text?html}
<i>${line.date}</i><#if line.mine && data.manage> · <i>yours</i></#if>

</#list>
<#if data.manage && data.lines?size gt 0>Authors are hidden. 🚩 — report a comment, 🗑 — delete your own.</#if>
