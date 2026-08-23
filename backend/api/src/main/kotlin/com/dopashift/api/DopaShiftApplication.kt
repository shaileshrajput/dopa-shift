package com.dopashift.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["com.dopashift"])
@EnableScheduling
class DopaShiftApplication

fun main(args: Array<String>) {
    runApplication<DopaShiftApplication>(*args)
}
