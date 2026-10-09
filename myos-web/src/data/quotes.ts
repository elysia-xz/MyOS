/**
 * 复盘页的格言。
 *
 * 全部取自公有领域的中国古典典籍与古文（论语/荀子/道德经/礼记/孟子等），
 * 不涉及任何现代版权文本。默认按「当日」取一句，保证一天之内稳定不变。
 */
export interface Quote {
  text: string
  source: string
}

export const QUOTES: Quote[] = [
  { text: '吾日三省吾身。', source: '《论语·学而》' },
  { text: '学而不思则罔，思而不学则殆。', source: '《论语·为政》' },
  { text: '见贤思齐焉，见不贤而内自省也。', source: '《论语·里仁》' },
  { text: '君子求诸己，小人求诸人。', source: '《论语·卫灵公》' },
  { text: '士不可以不弘毅，任重而道远。', source: '《论语·泰伯》' },
  { text: '君子博学而日参省乎己，则知明而行无过矣。', source: '《荀子·劝学》' },
  { text: '不积跬步，无以至千里；不积小流，无以成江海。', source: '《荀子·劝学》' },
  { text: '锲而不舍，金石可镂。', source: '《荀子·劝学》' },
  { text: '合抱之木，生于毫末；九层之台，起于累土。', source: '《道德经》' },
  { text: '千里之行，始于足下。', source: '《道德经》' },
  { text: '知人者智，自知者明；胜人者有力，自胜者强。', source: '《道德经》' },
  { text: '行有不得者，皆反求诸己。', source: '《孟子·离娄上》' },
  { text: '博学之，审问之，慎思之，明辨之，笃行之。', source: '《礼记·中庸》' },
  { text: '凡事预则立，不预则废。', source: '《礼记·中庸》' },
  { text: '知不足，然后能自反也；知困，然后能自强也。', source: '《礼记·学记》' },
  { text: '玉不琢，不成器；人不学，不知道。', source: '《礼记·学记》' },
  { text: '苟日新，日日新，又日新。', source: '《礼记·大学》' },
  { text: '静以修身，俭以养德。', source: '诸葛亮《诫子书》' },
  { text: '非学无以广才，非志无以成学。', source: '诸葛亮《诫子书》' },
  { text: '业精于勤，荒于嬉；行成于思，毁于随。', source: '韩愈《进学解》' },
  { text: '博观而约取，厚积而薄发。', source: '苏轼《稼说送张琥》' },
  { text: '古之立大事者，不惟有超世之才，亦必有坚忍不拔之志。', source: '苏轼《晁错论》' },
  { text: '纸上得来终觉浅，绝知此事要躬行。', source: '陆游《冬夜读书示子聿》' },
  { text: '问渠那得清如许？为有源头活水来。', source: '朱熹《观书有感》' },
  { text: '天下事有难易乎？为之，则难者亦易矣。', source: '彭端淑《为学》' }
]

/** 按日期取当日格言：同一天内始终是同一句，跨天自动更换 */
export function quoteOfDay(d: Date = new Date()): Quote {
  const start = new Date(d.getFullYear(), 0, 0)
  const dayOfYear = Math.floor((d.getTime() - start.getTime()) / 86400000)
  return QUOTES[dayOfYear % QUOTES.length]
}
