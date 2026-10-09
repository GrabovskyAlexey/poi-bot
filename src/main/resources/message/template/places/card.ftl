<b>${data.name?html}</b>
<#if data.address??>📍 ${data.address?html}
</#if><#if data.distanceText??>📏 ${data.distanceText} от вас
</#if><#if data.website??>🔗 ${data.website?html}
</#if><#if data.description??>
${data.description?html}</#if>