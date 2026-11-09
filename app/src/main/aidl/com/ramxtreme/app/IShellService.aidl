package com.ramxtreme.app;

interface IShellService {
    // Code réservé par Shizuku pour arrêter un UserService.
    void destroy() = 16777114;

    // Exécute une commande de la liste blanche. Réponse : "<code de sortie>\n<sortie>".
    String exec(String command) = 1;
}
