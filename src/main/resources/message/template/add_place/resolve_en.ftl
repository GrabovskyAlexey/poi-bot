<#if data.strong>Looks like this place is already in the bot:
<#else>There are already places nearby that were added to the bot. Is it one of them?
</#if>
<#list data.candidates as c>
• <b>${c.name?html}</b> — ${c.distanceText}<#if c.address??>, ${c.address?html}</#if>
</#list>
<#if data.strong>
Is it the same place?
</#if>

<i>This way ratings and comments will refer to one place. Your name, photo and description stay unchanged.</i>
