<b>${data.name?html}</b>
<#if data.rating??>⭐ ${data.rating}
</#if><#if data.statusText??>${data.statusText}
</#if><#if data.address??>📍 ${data.address?html}
</#if><#if data.distanceText??>📏 ${data.distanceText} от вас
</#if><#if data.website??>🔗 ${data.website?html}
</#if><#if data.tags?has_content>🏷 <#list data.tags as tag>#${tag?html}<#sep> </#list>
</#if><#if data.description??>
${data.description?html}
</#if><#if data.note??>
🗒 ${data.note?html}</#if>