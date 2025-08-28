[English ](README.md) | [Русский ](README_RU.md)

<br>

<img src="https://github.com/UlyssesZh/SunProxy/blob/master/app/src/main/ic_launcher-playstore.png?raw=true" width="100" alt="icon">

# SunProxy

Используйте VPN для прокси (перенаправление TCP-пакетов, включая HTTP-прокси), собственный DNS и собственный файл hosts.

## Синтаксис правил перенаправления

Мне лень объяснять.
См. примеры в `app/src/test/java/io/github/ulysseszh/sunproxy/UtilsTest.kt`.

## Примечания по использованию

Для функций DNS отключите приватный DNS в настройках системы и браузера.

Правила перенаправления, основанные на имени хоста, не работают для HTTP, но работают для HTTPS (иногда).
Это связано с тем, что HTTP-заголовки не помещаются в один TCP-пакет,
а сокет уже открыт при инициации первого пакета.
В случае HTTPS имя хоста читается из SNI в TLS рукопожатии,
поэтому имя хоста известно до открытия сокета.

Правила перенаправления, основанные на наличии TLS, также ненадежны,
потому что на самом деле они зависят от наличия SNI в TLS рукопожатии.
TLS без SNI ошибочно определяется как не-TLS.

## Скриншоты

<img src="https://raw.githubusercontent.com/UlyssesZh/SunProxy/master/metadata/en-US/images/phoneScreenshots/main.png?raw=true" width="300" alt="main"><img src="https://raw.githubusercontent.com/UlyssesZh/SunProxy/master/metadata/en-US/images/phoneScreenshots/settings.png?raw=true" width="300" alt="settings">

## Сборка

```
./gradlew build
```

## Лицензия

Этот проект лицензирован под GPL-3.0-or-later.

Весь проект является переписыванием [TunProxy](https://github.com/raise-isayan/TunProxy),
который, в свою очередь, является форком tun2http (оригинальный репозиторий удалён),
который не имеет открытой лицензии, но должен быть GPL-3.0-or-later,
поскольку содержит код под GPL-3.0-or-later из
[NetGuard](https://github.com/M66B/NetGuard).
Также содержал код из
[SNI Proxy](https://github.com/dlundquist/sniproxy),
лицензированного по BSD-2-Clause.

В этой переписке я использовал код из последнего коммита (bdf74ec) NetGuard,
и восстановил оригинальное уведомление о лицензии NetGuard в исходных кодах,
а также отметил все свои изменения.
Иконка приложения основана на иконке Material, лицензированной под Apache-2.0.
