📩 <i>Вам отправили место</i>

<b>${data.name?html}</b>
<#if data.rating??>⭐ ${data.rating}
</#if><#if data.address??>📍 ${data.address?html}
</#if><#if data.website??>🔗 ${data.website?html}
</#if><#if data.description??>
${data.description?html}</#if>
