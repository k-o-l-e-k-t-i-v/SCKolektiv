# SCKolektiv

Supercollider class for shared livecoding performance, networked play and textual recording of sessions.

It can be used for livecoding in-place with one player connected to sound system or distant session where each player can hear exactly the same on his own server.

The addresses are hard-coded (see source) so far, please modify according to IP adresses of particular players.

For distant internet connection OpenVPN setup is well advised.

#initialization 

    Kolketiv(\name)

#deinitialization

    Kolektiv.free
