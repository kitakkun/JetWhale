package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.agent.runtime.KtorLogLevel
import com.kitakkun.jetwhale.agent.runtime.LogLevel
import com.kitakkun.jetwhale.agent.runtime.startJetWhale

fun initializeJetWhale() {
    startJetWhale {
        connection {
            host = "localhost"
            port = 5443
            ssl {
                this.trustCertificate(
                    """
                    -----BEGIN CERTIFICATE-----
                    MIIBfjCCASOgAwIBAgIVAJL+9ulKVuFdLYkSAvouBbGpzG6SMAoGCCqGSM49BAMC
                    MBwxGjAYBgNVBAMMEUpldFdoYWxlIExvY2FsIENBMB4XDTI2MDcxODE3MDA0OVoX
                    DTM2MDcxNTE3MDA0OVowHDEaMBgGA1UEAwwRSmV0V2hhbGUgTG9jYWwgQ0EwWTAT
                    BgcqhkjOPQIBBggqhkjOPQMBBwNCAATYJSrq7gZ7iu1s5M2IRooCY5EheYniVV+h
                    m1Uan1twZhpY90xzPiaaPn7r6DTvIgnL78UugdTLGGa8bbBIyjEco0IwQDAPBgNV
                    HRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUOFk/3yAwgcLf
                    eK4dQPxd61ZJ7bMwCgYIKoZIzj0EAwIDSQAwRgIhAIaqH2ndGadE8y+xOi4s7A45
                    D/3BPRM1T2mdvjRObCe4AiEA6zzXT3PhPNsHO2zs8c266rI/6nERxyQsEe9ruUox
                    p6g=
                    -----END CERTIFICATE-----
                    """.trimIndent(),
                )
            }
        }

        logging {
            enabled = true
            logLevel = LogLevel.WARN
            ktorLogLevel = KtorLogLevel.NONE
        }

        plugins {
            register(DIModule.exampleAgentPlugin)
            register(DIModule.networkAgentPlugin)
        }
    }
}
