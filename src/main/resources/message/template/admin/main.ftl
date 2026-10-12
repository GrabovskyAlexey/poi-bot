📊 <b>Статистика</b>
<i>на ${data.generatedAt} МСК</i>

👥 <b>Аудитория</b>
Всего: <b>${data.stats.audience.total}</b> · добавили места: ${data.stats.audience.withPlaces} (${data.withPlacesPercent}%)
Новые: сегодня ${data.stats.audience.new1} · 7 дн. ${data.stats.audience.new7} · 30 дн. ${data.stats.audience.new30}
Активные: сегодня ${data.stats.audience.active1} · 7 дн. ${data.stats.audience.active7} · 30 дн. ${data.stats.audience.active30}

📍 <b>Контент</b>
Мест у пользователей: ${data.stats.content.savedPlaces} (уникальных заведений: ${data.stats.content.uniquePlaces})
Групп: ${data.stats.content.groups} · публикаций в группах: ${data.stats.content.publications}
Оценок: ${data.stats.content.ratings} · комментариев: ${data.stats.content.comments} (скрыто: ${data.stats.content.hiddenComments})
Действующих ссылок-приглашений: ${data.stats.content.activeLinks}

📈 <b>По неделям</b> (с понедельника, последняя строка - текущая неделя)
<pre>${data.weekTable?html}</pre>
