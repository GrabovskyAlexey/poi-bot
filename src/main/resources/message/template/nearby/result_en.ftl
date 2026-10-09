<#if data.nothingAnywhere>Nothing found nearby (within 1 km).<#else><#if data.points?size == 0>Nothing within <b>${data.radiusText}</b>.<#else><b>Within ${data.radiusText}:</b>
<#list data.points as p>
${p.index}. <b>${p.name?html}</b> — ${p.distanceText}
</#list>
<#if data.hiddenCount gt 0>…and ${data.hiddenCount} more
</#if></#if><#if data.hints?size gt 0>

<#list data.hints as h>
🔎 Within ${h.radiusText} — ${h.extra} more (${h.total} total)
</#list></#if></#if>
