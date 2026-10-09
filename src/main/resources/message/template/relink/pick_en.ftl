🔀 <b>Different place</b>

There are places nearby that are already in the bot. If this is the same venue, merge your record with it — ratings and comments will be shared. Your name, photo and description stay unchanged.

<#list data.candidates as c>
• <b>${c.name?html}</b> — ${c.distanceText}<#if c.address??>, ${c.address?html}</#if>
</#list>

Choose a place:
