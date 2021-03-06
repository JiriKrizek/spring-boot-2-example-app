package app.customer

import app.kAnyObject
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class CustomerServiceTest {
    companion object {
        private val customer = generateCustomer()
    }

    @Test
    fun findById() {
        val repository = mock(CustomerRepository::class.java)
        val service = CustomerService(repository)

        given(repository.findById(customer.id)).willReturn(Mono.just(customer))

        StepVerifier
            .create(service.findById(customer.id))
            .expectNext(customer)
            .verifyComplete()

        verify(repository, times(1)).findById(customer.id)
    }

    @Test
    fun `findById not found`() {
        val repository = mock(CustomerRepository::class.java)
        val service = CustomerService(repository)

        given(repository.findById(kAnyObject(String::class.java)))
            .willReturn(Mono.empty())

        StepVerifier
            .create(service.findById(customer.id))
            .expectError(ResponseStatusException::class.java)
            .verify()

        verify(repository, times(1)).findById(customer.id)
    }

    @Test
    fun findByName() {
        val repository = mock(CustomerRepository::class.java)
        val service = CustomerService(repository)

        given(repository.findByName(customer.name)).willReturn(Flux
            .just(customer))

        StepVerifier
            .create(service.findByName(customer.name))
            .expectNext(customer)
            .verifyComplete()

        verify(repository, times(1)).findByName(customer.name)
    }

    @Test
    fun `findByName not found`() {
        val repository = mock(CustomerRepository::class.java)
        val service = CustomerService(repository)

        given(repository.findByName(kAnyObject(String::class.java)))
            .willReturn(Flux.empty())

        StepVerifier
            .create(service.findByName(customer.name))
            .expectError(ResponseStatusException::class.java)
            .verify()

        verify(repository, times(1)).findByName(customer.name)
    }

    @Test
    fun save() {
        val repository = mock(CustomerRepository::class.java)
        val service = CustomerService(repository)

        given(repository.save(customer)).willReturn(Mono.just(customer))
        service.save(customer).block()
        verify(repository, times(1)).save(customer)
    }

    @Test
    fun deleteById() {
        val repository = mock(CustomerRepository::class.java)
        val service = CustomerService(repository)

        given(repository.deleteById(customer.id)).willReturn(Mono.empty())
        service.deleteById(customer.id).block()
        verify(repository, times(1)).deleteById(customer.id)
    }
}
