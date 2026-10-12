<#if data.editing><b>✏️ Edit place</b><#else><b>➕ New place</b></#if>

<b>Name:</b> <#if data.name??>${data.name?html}<#else>—</#if>
<b>Address:</b> <#if data.address??>${data.address?html}<#else>—</#if>
<b>Location:</b> <#if data.hasLocation>✅<#else>—</#if>
<b>Photo:</b> <#if data.hasPhoto>✅<#else>—</#if>
<b>Website:</b> <#if data.website??>${data.website?html}<#else>—</#if>
<b>Description:</b> <#if data.description??>${data.description?html}<#else>—</#if>
<b>Tags:</b> <#if data.tags?has_content><#list data.tags as tag>#${tag?html}<#sep> </#list><#else>—</#if>
<b>Note:</b> <#if data.note??>${data.note?html}<#else>—</#if>
<b>Status:</b> <#if data.statusText??>${data.statusText}<#else>—</#if>

<i>Fill in the fields in any order: use the buttons or just send the bot a location, photo, venue, link or text. Only the name is required.

💡 You can send a place link from Yandex Maps, Google Maps or 2GIS (“Share” → copy link): the bot will fill in the name, address and location, as far as it can.</i>
