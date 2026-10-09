<#if data.editing><b>✏️ Edit place</b><#else><b>➕ New place</b></#if>

<b>Name:</b> <#if data.name??>${data.name?html}<#else>—</#if>
<b>Address:</b> <#if data.address??>${data.address?html}<#else>—</#if>
<b>Location:</b> <#if data.hasLocation>✅<#else>—</#if>
<b>Photo:</b> <#if data.hasPhoto>✅<#else>—</#if>
<b>Website:</b> <#if data.website??>${data.website?html}<#else>—</#if>
<b>Description:</b> <#if data.description??>${data.description?html}<#else>—</#if>

<i>Fill in the fields in any order: use the buttons or just send the bot a location, photo, venue, link or text. Only the name is required.</i>
