package car.mazda.obd.android.ui.command

sealed class MainViewCommand {
    data object SoundGreeting : MainViewCommand()
}
