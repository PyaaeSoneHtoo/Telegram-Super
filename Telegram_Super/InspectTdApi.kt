import org.drinkless.tdlib.TdApi

fun main() {
    val constructor = TdApi.SendMessage::class.java.constructors.first()
    println(constructor)
}
