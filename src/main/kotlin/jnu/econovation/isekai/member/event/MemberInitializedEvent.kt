package jnu.econovation.isekai.member.event

import org.springframework.context.ApplicationEvent

class MemberInitializedEvent(source: Any) : ApplicationEvent(source)