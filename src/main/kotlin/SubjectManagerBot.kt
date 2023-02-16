import GlobalLogger.RED
import GlobalLogger.RESET
import GlobalLogger.globalLogger
import GlobalLogger.logButtonInteractionEnter
import GlobalLogger.logButtonInteractionLeave
import GlobalLogger.logFunctionEnter
import GlobalLogger.logFunctionLeave
import GlobalLogger.logModalInteractionEnter
import GlobalLogger.logModalInteractionLeave
import Utility.clearChannel
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Category
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import Utility.Channels
import Utility.StudyDirection
import Utility.Categories
import Utility.getChannel
import Utility.getCategory
import Utility.normalizeChanelName
import Utility.sendMessageAndDeferReply
import net.dv8tion.jda.api.entities.Role

class SubjectManagerBot : ListenerAdapter() {

    private val createButton = Button.primary("channelCreation", "Создать учебный курс")
    private val joinButton = Button.primary("channelJoining", "Присоединиться к учебному курсу")

    override fun onGuildReady(event: GuildReadyEvent) {
        logFunctionEnter(Throwable().stackTrace[0].methodName, this.javaClass.name)

        val guild = event.guild
        val subjectCreationChannel = getChannel(
            Channels.SUBJECT_CREATION,
            getCategory(Categories.SUBJECT_MANAGEMENT, guild)
        )

        clearChannel(subjectCreationChannel)

        val channelListMention = "Ознакомиться с полным списком уже созданных курсов Вы можете тут: " +
                getChannel(
                    Channels.SUBJECT_LIST,
                    getCategory(Categories.SUBJECT_MANAGEMENT, guild)
                ).asMention

        subjectCreationChannel.sendMessage(
            "Этот чат предназначен для создания каналов для учебных курсов. $channelListMention"
        ).queue()
        subjectCreationChannel.sendMessage(
            "Для создания учебного курса нажмите на кнопку ниже. \n" +
                    "В форме введите название предмета (к нему автоматически будет приписан текущий учебный год), " +
                    "семестр проведения курса (можно оставить пустым) и номера курсов для каждого направления, " +
                    "для которых Ваш учебный курс является обязательным (тоже можно оставить пустыми)."
        ).setActionRow(createButton).queue()

        val channelJoining = getChannel(
            Channels.SUBJECT_JOINING,
            getCategory(Categories.SUBJECT_MANAGEMENT, guild)
        )
        clearChannel(channelJoining)

        channelJoining.sendMessage(channelListMention).setActionRow(joinButton).queue()

        logFunctionLeave(Throwable().stackTrace[0].methodName,this.javaClass.name)
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        if (event.componentId !in listOf(
                "channelCreation",
                "channelJoining"
            )
        ) return

        logButtonInteractionEnter(
            Throwable().stackTrace[0].methodName,
            this.javaClass.name,
            event.componentId,
            event.user.asTag
        )

        val courseNumberSuggestions = listOf("1, 2, 3, 4", "2", "1, 4", "3", "3", "3", "3", "3", "3")

        val allStudyDirectionActionRows = enumValues<StudyDirection>().mapIndexed { index, studyDirection -> ActionRow.of(
                TextInput.create(studyDirection.name, studyDirection.label, TextInputStyle.SHORT)
                    .setPlaceholder(courseNumberSuggestions[index])
                    .setRequired(false)
                    .build()
            )
        }

        val subjectNameActionRow = ActionRow.of(
            TextInput.create("subjectName",
                "Название предмета",
                TextInputStyle.SHORT
            ).setRequiredRange(1, 100)
            .setPlaceholder("Теоретическая информатика (практика)")
            .build()
        )

        val semesterNumberRow = ActionRow.of(
            TextInput.create("semesterNumber",
                "Номер семестра проведения",
                TextInputStyle.SHORT
            ).setRequiredRange(0, 1)
                .setRequired(false)
                .build()
        )

        when (event.componentId) {
            "channelCreation" -> {
                val subjectCreation = Modal.create(
                    "channelCreation",
                    "Создание учебного курса"
                ).addActionRows(subjectNameActionRow)
                    .addActionRows(semesterNumberRow)
                    .addActionRows(allStudyDirectionActionRows).build()

                event.replyModal(subjectCreation).queue()
            }
            "channelJoining" -> {
                val subjectJoin = Modal.create("channelJoining",
                    "Присоединение к учебному курсу")
                    .addActionRows(subjectNameActionRow)
                    .build()

                event.replyModal(subjectJoin).queue()
            }
        }
        logButtonInteractionLeave(
            Throwable().stackTrace[0].methodName,
            this.javaClass.name,
            event.componentId,
            event.user.asTag
        )
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (event.modalId !in listOf(
                "channelCreation",
                "channelJoining"
            )
        ) return

        logModalInteractionEnter(
            Throwable().stackTrace[0].methodName,
            this.javaClass.name,
            event.modalId,
            event.user.asTag
        )

        val member = event.member  ?: let {
            sendMessageAndDeferReply(
                event, "Wrong accept confirmation: " +
                        "there is no member in processing event.\n " +
                        "Please, tell dummy programmers about that, and they will definitely fix that."
            )
            globalLogger.error(RED + "Member was not found in event " +
                    "in ${Throwable().stackTrace[0].methodName} at ${this.javaClass.name}" + RESET)
            return@onModalInteraction
        }

        val guild = event.guild ?: return
        val subjectName = normalizeChanelName(event.getValue("subjectName")?.asString
            ?: let {
                sendMessageAndDeferReply(
                    event, "Wrong accept confirmation: " +
                            "there is no subjectName in processing event.\n " +
                            "Please, tell dummy programmers about that, and they will definitely fix that."
                )
                globalLogger.error(RED + "SubjectName was not found in event " +
                        "in ${Throwable().stackTrace[0].methodName} at ${this.javaClass.name}" + RESET)
                return@onModalInteraction
            }
        )

        fun createSubjectChannel(category: Category, roles: List<Role>) {
            if (category.textChannels.map { it.name }.contains(subjectName)) {
                event.deferReply(true).queue()
                event.hook.sendMessage("Канал с таким именем уже существует. " +
                        "Присоединитесь к нему вместо создания.").setEphemeral(true).complete()
                return
            }
            category.createTextChannel(subjectName).addMemberPermissionOverride(
                member.idLong, mutableListOf(
                    Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MANAGE_CHANNEL
                ),
                null
            ).queue()

            event.deferReply(true).queue()
            event.hook.sendMessage("Учебный курс $subjectName успешно создан!")
                .setEphemeral(true).complete()
        }

        val subjectsCategory = getCategory(Categories.SUBJECTS, guild)

        //считать данные из формы про обязательные курсы

        when(event.modalId) {
            "channelCreation" -> createSubjectChannel(subjectsCategory, emptyList())
            "channelJoining" -> {
                val channel = subjectsCategory.textChannels.find { it.name == subjectName } ?: let {
                    event.deferReply(true).queue()
                    event.hook.sendMessage("Учебного курса с указанным именем не существует.\n" +
                            "Проверьте введённые данные и попробуйте ещё раз. Список курсов Вы можете найти здесь:" +
                            getChannel(Channels.SUBJECT_LIST,
                                getCategory(Categories.SUBJECT_MANAGEMENT, guild)
                            ).asMention
                    ).setEphemeral(true).complete()
                    return@onModalInteraction
                }
                channel.manager.putMemberPermissionOverride(
                    member.idLong, mutableListOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null
                ).queue()

                event.deferReply(true).queue()
                event.hook.sendMessage("Вы успешно записаны на курс. " +
                        "Его можно найти в категории 'Учебные курсы' слева.")
                    .setEphemeral(true).complete()
                return
            }
        }

        logModalInteractionLeave(
            Throwable().stackTrace[0].methodName,
            this.javaClass.name,
            event.modalId,
            event.user.asTag
        )
    }
}