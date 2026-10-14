🚫 <b>Скрытые комментарии</b> (${data.position} из ${data.total})

📍 <b>${data.comment.placeName?html}</b><#if data.comment.placeAddress??>
${data.comment.placeAddress?html}</#if>
Жалоб: ${data.comment.reports}

<blockquote>${data.comment.text?html}</blockquote>

<i>«Вернуть» снимает скрытие и сбрасывает жалобы, «Удалить» стирает комментарий насовсем.</i>
