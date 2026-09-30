package com.example.productsearch.common

import com.example.productsearch.search.SortOption
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.format.FormatterRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class WebConfiguration : WebMvcConfigurer {
    override fun addFormatters(registry: FormatterRegistry) {
        // Accept the documented lower-case values (price_asc) rather than enum constant names.
        registry.addConverter(SortOptionConverter())
    }

    private class SortOptionConverter : Converter<String, SortOption> {
        override fun convert(source: String): SortOption = SortOption.fromParam(source)
    }

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
