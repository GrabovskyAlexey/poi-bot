<#if data.error??>⚠️ ${data.error?html}

</#if><#switch data.field>
<#case "name">Введите <b>название</b> места:<#break>
<#case "address">Введите <b>адрес</b> текстом:<#break>
<#case "location">Отправьте <b>геопозицию</b> (📎 → Геопозиция) или место из карты (venue):<#break>
<#case "photo">Отправьте <b>фото</b> места:<#break>
<#case "website">Отправьте <b>ссылку</b> на сайт:<#break>
<#case "description">Введите <b>описание</b>:<#break>
<#case "tags">Введите <b>теги</b> через запятую или пробел (до 5, каждый до 30 символов) или выберите из подсказок. Теги видны только вам:<#break>
<#case "note">Введите личную <b>заметку</b> (видна только вам):<#break>
</#switch>
