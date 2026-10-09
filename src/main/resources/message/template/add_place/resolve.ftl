<#if data.strong>Похоже, это место уже есть в боте:
<#else>Рядом уже есть места, добавленные в бот. Это одно из них?
</#if>
<#list data.candidates as c>
• <b>${c.name?html}</b> — ${c.distanceText}<#if c.address??>, ${c.address?html}</#if>
</#list>
<#if data.strong>
Это то же самое место?
</#if>

<i>Так рейтинг и комментарии будут относиться к одному месту. Ваше название, фото и описание не изменятся.</i>
