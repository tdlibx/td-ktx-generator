# td-ktx-generator
Telegram API Kotlin extensions generator

Script for parsing [TdApi] Java classes using [PSI] and generating Koltin extensions for them using [KotlinPoet]

## Using
Set ``apiPath`` to the place where ``TdApi.java`` is located and ``outputPath`` for a generated files folder root.
Run ``GENERATE`` configuration or execute [generate.sh](https://github.com/tdlibx/td-ktx-generator/blob/master/tdktxgen/generate.sh) script


[TdApi]: https://github.com/tdlibx/td
[PSI]: https://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/psi.html
[KotlinPoet]: https://github.com/square/kotlinpoet
