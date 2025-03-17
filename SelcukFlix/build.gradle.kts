version = 1

cloudstream {
    authors     = listOf("keyiflerolsun, pltmustafa")
    language    = "tr"
    description = "Binlerce popüler yabancı dizi izle ve film izle seçeneğiyle dolu platformumuzda, istediğiniz içeriğe anında erişin."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 3 // will be 3 if unspecified
    tvTypes = listOf("Movie")
    iconUrl = "https://selcukflix.com/assets/images/slogof.svg"
}
