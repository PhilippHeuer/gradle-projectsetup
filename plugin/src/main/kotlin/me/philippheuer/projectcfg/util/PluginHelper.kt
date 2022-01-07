package me.philippheuer.projectcfg.util

import java.io.File

class PluginHelper {
    companion object {
        /**
         * managed file
         */
        fun createOrUpdatePropertyFile(
            file: String,
            properties: Map<String, String>,
            managed: Boolean = false,
        ) {
            // write config
            val content = StringBuilder()
            if (managed) {
                content.append("# DO NOT EDIT! This file contains defaults and is managed automatically generated by the project configuration gradle plugin.\n")
            }

            properties.forEach {
                content.append("${it.key}=${it.value}\n")
            }
            val defaultPropertyFile = File(file)
            defaultPropertyFile.parentFile.mkdirs()
            defaultPropertyFile.writeText(content.toString())
        }
    }
}