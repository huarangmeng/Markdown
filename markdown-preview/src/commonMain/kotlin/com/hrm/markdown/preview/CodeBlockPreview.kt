package com.hrm.markdown.preview

import androidx.compose.runtime.Composable
import com.hrm.markdown.renderer.Markdown

internal val codeBlockPreviewGroups = listOf(
    PreviewGroup(
        id = "kotlin_code",
        title = "Kotlin 代码",
        description = "Kotlin 语法高亮",
        items = listOf(
            PreviewItem(
                id = "kotlin_basic",
                title = "基础 Kotlin",
                content = {
                    Markdown(
                        markdown = """
```kotlin
fun main() {
    val message = "Hello, Markdown!"
    println(message)
    
    val numbers = listOf(1, 2, 3, 4, 5)
    numbers.filter { it % 2 == 0 }
           .map { it * it }
           .forEach { println(it) }
}
```
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "kotlin_class",
                title = "Kotlin 类定义",
                content = {
                    Markdown(
                        markdown = """
```kotlin
@Serializable
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
)

class UserRepository(private val api: ApiClient) {
    suspend fun getUsers(): List<User> = api.getUsers()
    suspend fun getUserById(id: Long): User? = api.getUserById(id)
}
```
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "python_code",
        title = "Python 代码",
        description = "Python 语法高亮",
        items = listOf(
            PreviewItem(
                id = "python_basic",
                title = "Python 示例",
                content = {
                    Markdown(
                        markdown = """
```python
# Python 语法高亮示例
def fibonacci(n: int) -> list:
    result = [0, 1]
    for i in range(2, n):
        result.append(result[-1] + result[-2])
    return result

@dataclass
class Config:
    name: str = "default"
    count: int = 100
    enabled: bool = True
```
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "json_code",
        title = "JSON 数据",
        description = "JSON 语法高亮",
        items = listOf(
            PreviewItem(
                id = "json_basic",
                title = "JSON 示例",
                content = {
                    Markdown(
                        markdown = """
```json
{
  "name": "markdown-renderer",
  "version": "1.0.0",
  "features": ["syntax-highlight", "lazy-inline"],
  "enabled": true,
  "config": {
    "theme": "dark",
    "fontSize": 14
  }
}
```
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "code_attributes",
        title = "代码块属性",
        description = "通用属性语法 {.class #id key=value}",
        items = listOf(
            PreviewItem(
                id = "attr_class_id",
                title = "CSS class 与 ID",
                content = {
                    Markdown(
                        markdown = """
```kotlin {.highlight #example-block}
fun main() {
    println("This block has .highlight class and #example-block id")
}
```
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "attr_key_value",
                title = "自定义 key=value 属性",
                content = {
                    Markdown(
                        markdown = """
```python {data-line="3-5" title="fibonacci.py"}
def fibonacci(n):
    if n <= 1:
        return n
    return fibonacci(n-1) + fibonacci(n-2)

print(fibonacci(10))
```
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "attr_mixed",
                title = "混合属性",
                content = {
                    Markdown(
                        markdown = """
```json {.config #app-settings theme="dark" readonly=true}
{
  "name": "my-app",
  "version": "2.0.0",
  "debug": false
}
```
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "multi_language",
        title = "多语言代码块",
        description = "多种语言对比展示",
        items = listOf(
            PreviewItem(
                id = "multi_lang",
                title = "Kotlin + Python + JSON",
                content = {
                    Markdown(
                        markdown = """
Kotlin:

```kotlin
fun greet(name: String) = "Hello, ${'$'}name!"
```

Python:

```python
def greet(name: str) -> str:
    return f"Hello, {name}!"
```

JSON:

```json
{"greeting": "Hello, World!"}
```
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
)
