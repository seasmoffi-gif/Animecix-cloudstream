version = 20

cloudstream {
    authors     = listOf("yusiqo")
    language    = "tr"
    description = "Dahilinde birçok Fansub Barındıran Özel Servis API (Açık Kaynak) ."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Anime")
    iconUrl = "https://anm.cx/favicon.ico"
}
