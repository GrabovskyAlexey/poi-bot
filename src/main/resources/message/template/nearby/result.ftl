<#if data.nothingAnywhere>Рядом (до 1 км) ничего не найдено.<#else><#if data.points?size == 0>В радиусе <b>${data.radiusText}</b> ничего нет.<#else><b>В радиусе ${data.radiusText}:</b>
<#list data.points as p>
${p.index}. <b>${p.name?html}</b> — ${p.distanceText}
</#list>
<#if data.hiddenCount gt 0>…и ещё ${data.hiddenCount}
</#if></#if><#if data.hints?size gt 0>

<#list data.hints as h>
🔎 В радиусе ${h.radiusText} — ещё ${h.extra} (всего ${h.total})
</#list></#if></#if>
