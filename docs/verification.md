# Проверка решений

Дата проверки: 10 октября 2026 года. Окружение: OpenJDK 27.0.0 (build 27+35-2325), macOS arm64.

## Автоматические проверки

Команда: `mise exec -- ./test.sh`.

- Компиляция всех исходников и тестов с `-Xlint:all`: без предупреждений.
- `IndexTest`: правильность ожидаемого индекса; одинаковые имена в разных пакетах; интерфейсы; generics с вложенными типами; аннотации; локальные классы; унаследованные вложенные типы; явные, wildcard и неоднозначные импорты; отсутствующие определения; повреждённые исходники и неверные пути.
- `IntegrationTest`: 90 проверок реальных запусков лабораторных. Сравниваются индексы лабораторных 2–6, числа исполнителей 1, 3 и 12, а также проект из 129 файлов при числе исполнителей 2. Проверяются пустые папки, ошибки синтаксиса без печати частичного индекса, отсутствие папки, неверное число исполнителей, Unicode, регистр и знаки препинания в счётчике слов.
- Для тестовых процессов установлен таймаут 30 секунд; зависаний не обнаружено.
- Пример в `examples/project` дополнительно скомпилирован настоящим javac.

## Демонстрация на Spring Framework

Источник: [официальный Spring Framework, тег v6.2.0](https://github.com/spring-projects/spring-framework/tree/v6.2.0/spring-core/src/main/java).

Коммит: `5024bb72279188f1d0dcfe19e8abdd4bdb9887c8`.

Проанализирована папка `spring-core/src/main/java`: **726 Java-файлов**. В третьей лабораторной создан отдельный виртуальный `Thread` для каждого файла. Все варианты выполнялись с `-Xmx512m -XX:ActiveProcessorCount=4`; у лабораторных 5 и 6 явно задано четыре исполнителя.

| Лабораторная | Код завершения | Сравнение с последовательным индексом |
|---|---|---|
| 2 | 0 | Эталон для сравнения |
| 3 | 0 | Полностью совпадает |
| 4 | 0 | Полностью совпадает |
| 5 | 0 | Полностью совпадает |
| 6 | 0 | Полностью совпадает |

Во всех результатах 1046 ключей и 634 непосредственных связей. SHA-256 одинакового stdout:

```text
fc0f7404296cdbd339c248bb16e57f638e6b805cc8e2896ee193ea3fec421fc3
```

Одна ссылка осталась неразрешённой: `Objenesis` у `org.springframework.objenesis.SpringObjenesis`. В выбранных исходниках нет объявления этого типа и нет явного импорта его полного имени. Все программы сохранили связь с отметкой `[unresolved ...]` и выдали предупреждение в stderr. Остальные ссылки, включая унаследованные вложенные типы, разрешены. Spring не собирался, его исходники не изменялись и не запускались.

## Как повторить демонстрацию

Из корня этого репозитория:

```sh
mkdir -p build/demo
git clone --depth 1 --filter=blob:none --sparse --branch v6.2.0 https://github.com/spring-projects/spring-framework.git build/demo/spring-framework
git -C build/demo/spring-framework sparse-checkout set spring-core/src/main/java
mise exec -- ./build.sh
mise exec -- java -Xmx512m -XX:ActiveProcessorCount=4 -cp build/classes lab2.Main build/demo/spring-framework/spring-core/src/main/java > build/spring-lab2.txt
mise exec -- java -Xmx512m -XX:ActiveProcessorCount=4 -cp build/classes lab3.Main build/demo/spring-framework/spring-core/src/main/java > build/spring-lab3.txt
diff -u build/spring-lab2.txt build/spring-lab3.txt
```

Пустой вывод `diff` означает совпадение. Предупреждение об `Objenesis` ожидаемо. Для запуска лабораторных 4–6 используйте их `Main`; лабораторные 5 и 6 принимают число исполнителей последним аргументом.
