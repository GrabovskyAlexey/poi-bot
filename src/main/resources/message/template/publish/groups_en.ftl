<b>📢 Publish to groups</b>

<#list data.placeNames as name>
• ${name?html}
</#list>
<#if data.moreCount gt 0>…and ${data.moreCount} more
</#if>

<#if data.groups?size == 0>
No groups available yet. Add the bot to the group you need, then press "➕ Choose a group" below and pick it.
<#else>
Tap a group to publish the places: ✅ — published, ➖ — only some, ⬜ — no. Tap again to unpublish.
Missing a group? Press "➕ Choose a group" below.
</#if>
