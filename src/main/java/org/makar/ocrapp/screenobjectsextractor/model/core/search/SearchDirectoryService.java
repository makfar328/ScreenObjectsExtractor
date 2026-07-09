package org.makar.ocrapp.screenobjectsextractor.model.core.search;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchDirectoryConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Сервис для обработки и управления конфигурациями директорий поиска.
 * Содержит инструменты для:
 * Реализующие логику "поглощения" каталогов.
 */
public class SearchDirectoryService {

    private static final Logger LOGGER = Logger.getLogger(SearchDirectoryService.class.getName());
    private final ObservableList<SearchDirectoryConfig> searchDirectories = FXCollections.observableArrayList();

    public SearchDirectoryService() {
        // Для персистентности в будущем буду сохранять в файл. Здесь будет загрузка из файла.
    }


    /**
     * Добавляет директорию для поиска, применяя логику поглощения.
     * @param newConfig Новая конфигурация директории.
     */
    public void addDirectory(SearchDirectoryConfig newConfig) {
        List<SearchDirectoryConfig> tempCombinedList = new ArrayList<>(searchDirectories);
        if (!tempCombinedList.contains(newConfig)) {
            tempCombinedList.add(newConfig);
        }

        List<SearchDirectoryConfig> absorbedList = applyAbsorptionLogic(tempCombinedList);

        searchDirectories.setAll(absorbedList);
        LOGGER.info("Добавлена директория: " + newConfig.getPath() + ". Текущие директории: " + searchDirectories.size());
    }


    /**
     * Применяет логику поглощения к списку конфигураций директорий [{Path p, ObservableInteger i},...].
     * Если директория-предок с достаточной глубиной поиска полностью покрывает диапазон поиска директории-потомка,
     * то потомок удаляется из списка.
     * @param configs
     * @return
     */
    public List<SearchDirectoryConfig> applyAbsorptionLogic(List<SearchDirectoryConfig> configs) {

        if (configs == null || configs.isEmpty()) {
            return new ArrayList<>(); // чтобы не вернуть null
        }

        List<SearchDirectoryConfig> currentConfigs = new ArrayList<>(configs);
        List<SearchDirectoryConfig> resultConfigs = new ArrayList<>();

        /*Set<Path> allPaths = configs.stream()
                .map(SearchDirectoryConfig::getDirectory)
                .collect(Collectors.toSet());*/

        currentConfigs.sort(Comparator.comparingInt(config -> config.getDirectory().getNameCount()));

        for (int i = 0; i < currentConfigs.size(); i++) {
            SearchDirectoryConfig config1 = currentConfigs.get(i);
            boolean absorbed = false;

            for (int j = 0; j < currentConfigs.size(); j++) {
                if (i == j) {continue;}
                SearchDirectoryConfig config2 = currentConfigs.get(j);
                if (config1.getDirectory().startsWith(config2.getDirectory())) {
                    /* config1 содержится в config2. */

                    /* проверка на то, тянется ли дочерний config1 дальше, чем содержащий его config2 */
                    if (config2.getSearchDepth() == -1 ||
                            config1.getSearchDepth() + config1.getDirectory().getNameCount() <= config2.getSearchDepth() + config2.getDirectory().getNameCount()) {
                        absorbed = true;
                        LOGGER.info(String.format("\"Директория '%s' (глубина %d) поглощена '%s' (глубина %d)",
                                config1.getDirectory(), config1.getSearchDepth(),
                                config2.getDirectory(), config2.getSearchDepth()));
                        break;
                    }
                }
            }

            if (!absorbed) {
                resultConfigs.add(config1);
            }

        }

        return resultConfigs;
    }


    /**
     * Удаляет директорию из списка сконфигурированных.
     * @param config Конфигурация директории для удаления.
     */
    public void removeDirectory(SearchDirectoryConfig config) {
        if (searchDirectories.remove(config)) {
            LOGGER.info("Удалена директория: " + config.getPath() + ". Текущие директории: " + searchDirectories.size());
            // Здесь можно добавить логику сохранения директорий
        } else {
            LOGGER.warning("Попытка удалить несуществующую директорию: " + config.getPath());
        }
    }


    /**
     * Возвращает неизменяемый список всех сконфигурированных директорий.
     * @return Неизменяемый список SearchDirectoryConfig.
     */
    public ObservableList<SearchDirectoryConfig> getDirectoryConfigs() {
        return FXCollections.unmodifiableObservableList(searchDirectories);
    }


    public void saveDirectories() {
        // TODO: Реализовать сохранение configuredDirectories в файл или БД
        LOGGER.info("Сохранение сконфигурированных директорий...");
    }


    public void loadDirectories() {
        // TODO: Реализовать загрузку configuredDirectories из файла или БД
        LOGGER.info("Загрузка сконфигурированных директорий...");
        // Пример: configuredDirectories.setAll(загруженный_список);
    }

}
