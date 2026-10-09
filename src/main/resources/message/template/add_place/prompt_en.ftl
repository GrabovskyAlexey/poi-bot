<#if data.error??>⚠️ ${data.error?html}

</#if><#switch data.field>
<#case "name">Enter the place <b>name</b>:<#break>
<#case "address">Enter the <b>address</b> as text:<#break>
<#case "location">Send a <b>location</b> (📎 → Location) or a venue:<#break>
<#case "photo">Send a <b>photo</b> of the place:<#break>
<#case "website">Send a <b>link</b> to the website:<#break>
<#case "description">Enter the <b>description</b>:<#break>
</#switch>